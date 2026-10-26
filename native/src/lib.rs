#![cfg_attr(not(test), deny(clippy::unwrap_used, clippy::expect_used))]
//! JNI accepts transient secrets unlocked by Android Keystore; no plaintext files.
mod application;
use ::client::Identity;
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jstring},
};
use std::panic::{AssertUnwindSafe, catch_unwind};
use zeroize::Zeroizing;

// DNS discovery uses Android's ConnectivityManager. Keep its Application context
// alive for the process, including background JNI calls after Activity recreation.
#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_initialize(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    context: jni::objects::JObject<'_>,
) {
    static CONTEXT: std::sync::OnceLock<jni::objects::GlobalRef> = std::sync::OnceLock::new();
    let result = (|| -> jni::errors::Result<()> {
        let vm = env.get_java_vm()?;
        let context = env.new_global_ref(context)?;
        CONTEXT.get_or_init(|| {
            // Both pointers remain valid for the process; OnceLock serializes the
            // sole initialization before Application.onCreate permits requests.
            unsafe {
                ndk_context::initialize_android_context(
                    vm.get_java_vm_pointer().cast(),
                    context.as_obj().as_raw().cast(),
                );
            }
            context
        });
        Ok(())
    })();
    if let Err(error) = result {
        let _ = env.throw_new("java/lang/IllegalStateException", error.to_string());
    }
}

const OPS_OPERATION: u8 = 3;
const IDENTITY_OPERATION: u8 = 4;
const MAX_REQUEST_BYTES: usize = 65536;

fn reply(env: &mut JNIEnv<'_>, result: Result<String, String>) -> jstring {
    match result.and_then(|value| env.new_string(value).map_err(|e| e.to_string())) {
        Ok(value) => value.into_raw(),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error);
            std::ptr::null_mut()
        }
    }
}
fn identity(env: &JNIEnv<'_>, secret: &JByteArray<'_>) -> Result<Identity, String> {
    let size = env.get_array_length(secret).map_err(|e| e.to_string())?;
    if !(1..=4096).contains(&size) {
        return Err("Invalid identity size".into());
    }
    let bytes = Zeroizing::new(env.convert_byte_array(secret).map_err(|e| e.to_string())?);
    Identity::import_secret(&bytes).map_err(|e| e.to_string())
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_generateSecret(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let bytes = Zeroizing::new(
            Identity::generate()
                .export_secret()
                .map_err(|e| e.to_string())?,
        );
        env.byte_array_from_slice(&bytes)
            .map(|a| a.into_raw())
            .map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native operation failed".into()));
    match result {
        Ok(array) => array,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error);
            std::ptr::null_mut()
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_peerId(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        Ok(identity(&env, &secret)?.peer_id().to_string())
    }))
    .unwrap_or_else(|_| Err("Native operation failed".into()));
    reply(&mut env, result)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_execute(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, 0)
}

#[derive(serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct OpsInput {
    address: String,
    peer: String,
    service: String,
    request: ::client::protocol::OpsRequest,
}
async fn execute_ops(
    identity: Identity,
    input: OpsInput,
) -> Result<::client::protocol::OpsResponse, String> {
    let (address, peer) =
        ::client::endpoint(&input.address, &input.peer).map_err(|e| e.to_string())?;
    let service = input.service.parse().map_err(|_| "Invalid service")?;
    let node = ::client::Node::start(identity, ::client::NodeConfig::default())
        .await
        .map_err(|e| e.to_string())?;
    let result = async {
        node.dial(address).await?;
        ::client::ops::execute(&node, peer, service, &input.request).await
    };
    let response = tokio::time::timeout(std::time::Duration::from_secs(150), result)
        .await
        .map_err(|_| "Operation timed out".to_string())
        .and_then(|r| r.map_err(|e| e.to_string()));
    node.shutdown().await.map_err(|e| e.to_string())?;
    response
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_executeOps(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, OPS_OPERATION)
}

/// TOFU bootstrap: fetch the identity genesis over an authenticated connection and
/// pin it locally and fetch the single application genesis when the host provides it.
async fn fetch_genesis(
    identity: Identity,
    text: &str,
) -> Result<serde_json::Value, ::client::Error> {
    #[derive(serde::Deserialize)]
    #[serde(deny_unknown_fields)]
    struct Input {
        address: String,
        peer: String,
    }
    let input: Input = serde_json::from_str(text).map_err(::client::Error::protocol)?;
    let node = ::client::Node::start(identity, ::client::NodeConfig::default()).await?;
    let result = async {
        let (genesis, pin) =
            ::client::trust::fetch_identity_genesis(&node, &input.address, &input.peer).await?;
        let application =
            ::client::trust::fetch_business_genesis::<::client::application::Application>(
                &node,
                &input.address,
                &input.peer,
                &pin,
                ::client::journal::PROTOCOL,
            )
            .await
            .map(|(_, pin)| serde_json::json!({"genesis_hash": pin}))
            .unwrap_or(serde_json::Value::Null);
        Ok::<serde_json::Value, ::client::Error>(serde_json::json!({
            "identity": {"genesis": genesis, "genesis_hash": pin},
            "application": application,
        }))
    };
    let response = tokio::time::timeout(std::time::Duration::from_secs(150), result)
        .await
        .map_err(|_| ::client::Error::Protocol("Operation timed out".into()))??;
    node.shutdown().await?;
    Ok(response)
}
const GENESIS_OPERATION: u8 = 6;
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_fetchGenesis(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, GENESIS_OPERATION)
}

const DISCOVER_OPERATION: u8 = 7;

/// LAN discovery comes from the shared client crate; announcements are untrusted
/// hints and the caller still pins the identity hash.
async fn discover(identity: Identity, text: &str) -> Result<serde_json::Value, ::client::Error> {
    #[derive(serde::Deserialize)]
    #[serde(deny_unknown_fields)]
    struct Input {
        #[serde(default)]
        wait_ms: Option<u64>,
    }
    let input: Input = serde_json::from_str(text).map_err(::client::Error::protocol)?;
    let servers = ::client::discovery::discover(
        identity,
        input.wait_ms.map(std::time::Duration::from_millis),
    )
    .await?;
    Ok(serde_json::json!({
        "servers": servers.into_iter().map(|server| serde_json::json!({
            "peer": server.peer.to_string(),
            "address": server.address,
            "federation": server.federation,
            "genesis_hash": server.genesis_hash,
            "application": server.application.map(|hash| serde_json::json!({"genesis_hash": hash})),
            "services": server.services,
        })).collect::<Vec<_>>()
    }))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_discover(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, DISCOVER_OPERATION)
}

fn request(
    env: &mut JNIEnv<'_>,
    secret: &JByteArray<'_>,
    input: JString<'_>,
    operation: u8,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(
        || -> Result<String, ::client::protocol::ClientFailure> {
            let identity = identity(env, secret)?;
            if env
                .call_method(&input, "length", "()I", &[])
                .and_then(|v| v.i())
                .map_err(|e| e.to_string())?
                > MAX_REQUEST_BYTES as i32
            {
                return Err(::client::Error::Capacity.failure());
            }
            let input: String = env.get_string(&input).map_err(|e| e.to_string())?.into();
            if input.len() > MAX_REQUEST_BYTES {
                return Err(::client::Error::Capacity.failure());
            }

            let runtime = tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .build()
                .map_err(|e| e.to_string())?;
            if operation == PUSH_OPERATION {
                let response = runtime
                    .block_on(application::push(identity, &input))
                    .map_err(|e| e.failure())?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
            } else if operation == IDENTITY_OPERATION {
                let input = serde_json::from_str(&input).map_err(invalid_request)?;
                let response = runtime
                    .block_on(::client::trust::execute_identity(identity, input))
                    .map_err(|e| e.failure())?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
            } else if operation == OPS_OPERATION {
                let input = serde_json::from_str(&input).map_err(invalid_request)?;
                let response = runtime.block_on(execute_ops(identity, input))?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
            } else if operation == GENESIS_OPERATION {
                let response = runtime
                    .block_on(fetch_genesis(identity, &input))
                    .map_err(|e| e.failure())?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
            } else if operation == DISCOVER_OPERATION {
                let response = runtime
                    .block_on(discover(identity, &input))
                    .map_err(|e| e.failure())?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
            } else {
                let signer = identity.clone();
                let request: serde_json::Value =
                    serde_json::from_str(&input).map_err(invalid_request)?;
                let genesis = request["genesis_hash"].as_str().unwrap_or("");
                let response = runtime
                    .block_on(application::execute(identity, &input))
                    .map_err(|e| e.failure())?;
                let presentation = response
                    .state
                    .as_ref()
                    .zip(response.identity_view.as_ref())
                    .zip(response.proof.as_ref())
                    .map(|((state, view), proof)| {
                        ::client::points::present(state, view, &signer, genesis, proof.observed_ms)
                    })
                    .transpose()
                    .map_err(|e| e.failure())?
                    .flatten();
                let mut value = serde_json::to_value(response).map_err(|e| e.to_string())?;
                if let Some(points) = presentation {
                    value["state"]["points_view"] =
                        serde_json::to_value(points).map_err(|e| e.to_string())?;
                }
                serde_json::to_string(&value).map_err(|e| e.to_string().into())
            }
        },
    ))
    .unwrap_or_else(|_| Err("Native operation failed".into()));
    let result = match result {
        Ok(value) => Ok(value),
        Err(error) => serde_json::to_string(
            &serde_json::json!({"error":error.message,"error_code":error.code}),
        )
        .map_err(|e| e.to_string()),
    };
    reply(env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_readFile(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    snapshot: JString<'_>,
    cid: JString<'_>,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jbyteArray, String> {
        let length = env
            .call_method(&snapshot, "length", "()I", &[])
            .and_then(|v| v.i())
            .map_err(|e| e.to_string())?;
        if length as usize > ::client::journal::MAX_SNAPSHOT {
            return Err("Snapshot too large".into());
        }
        let text: String = env.get_string(&snapshot).map_err(|e| e.to_string())?.into();
        let cid: String = env.get_string(&cid).map_err(|e| e.to_string())?.into();
        let snapshot: serde_json::Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let bytes = ::client::files::cached_file(&snapshot, &cid).map_err(|e| e.to_string())?;
        env.byte_array_from_slice(&bytes)
            .map(|a| a.into_raw())
            .map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native file operation failed".into()));
    match result {
        Ok(bytes) => bytes,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_executeIdentity(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, IDENTITY_OPERATION)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_publicIdentity(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        serde_json::to_string_pretty(&::client::management::PublicIdentity::from_identity(
            &identity(&env, &secret)?,
        ))
        .map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native identity operation failed".into()));
    reply(&mut env, result)
}
fn bounded_identity_text(env: &mut JNIEnv<'_>, input: &JString<'_>) -> Result<String, String> {
    let length = env
        .call_method(input, "length", "()I", &[])
        .and_then(|v| v.i())
        .map_err(|e| e.to_string())?;
    if length > MAX_REQUEST_BYTES as i32 {
        return Err("Identity input too large".into());
    }
    let text: String = env.get_string(input).map_err(|e| e.to_string())?.into();
    if text.len() > MAX_REQUEST_BYTES {
        return Err("Identity input too large".into());
    }
    Ok(text)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_management(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JString<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let text = bounded_identity_text(&mut env, &input)?;
        let request = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let result = ::client::management::execute(request).map_err(|e| e.to_string())?;
        serde_json::to_string(&result).map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native management operation failed".into()));
    reply(&mut env, result)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_signIdentityOffline(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        #[derive(serde::Deserialize)]
        #[serde(deny_unknown_fields)]
        struct Input {
            config: ::client::trust::Config,
            record: ::client::trust::TrustRecord,
        }
        let text = bounded_identity_text(&mut env, &input)?;
        let input: Input = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let result = ::client::management::sign_offline(
            &identity(&env, &secret)?,
            &input.config,
            input.record,
        )
        .map_err(|e| e.to_string())?;
        serde_json::to_string(&result).map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native identity operation failed".into()));
    reply(&mut env, result)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_reviewIdentityHistory(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JString<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let text = bounded_identity_text(&mut env, &input)?;
        if text.len() > MAX_REQUEST_BYTES {
            return Err("Identity review too large".into());
        }
        #[derive(serde::Deserialize)]
        #[serde(deny_unknown_fields)]
        struct Review {
            genesis: ::client::protocol::TrustGenesis,
            genesis_hash: String,
            records: Vec<::client::protocol::TrustRecord>,
        }
        let review: Review = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let state = ::client::trust::review(review.genesis, &review.genesis_hash, &review.records)
            .map_err(|e| e.to_string())?;
        serde_json::to_string(&state).map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native identity operation failed".into()));
    reply(&mut env, result)
}

fn invalid_request(error: impl ToString) -> ::client::protocol::ClientFailure {
    ::client::protocol::ClientFailure {
        code: ::client::protocol::ClientErrorCode::InvalidRequest,
        message: error.to_string(),
    }
}

const PUSH_OPERATION: u8 = 5;
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_executePush(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, PUSH_OPERATION)
}
