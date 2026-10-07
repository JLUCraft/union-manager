#![cfg_attr(not(test), deny(clippy::unwrap_used, clippy::expect_used))]
//! JNI accepts transient secrets unlocked by Android Keystore; no plaintext files.
mod office;
use ::client::{Identity, client};
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
    let peer = input.peer.parse().map_err(|_| "Invalid peer")?;
    let address: ::client::Multiaddr = input.address.parse().map_err(|_| "Invalid address")?;
    if !matches!(address.iter().last(), Some(::client::AddressProtocol::P2p(id)) if id == peer) {
        return Err("Address must match host identity".into());
    }
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
                    .block_on(office::push(identity, &input))
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
            } else if operation == 2 {
                let response = runtime
                    .block_on(office::execute(identity, &input))
                    .map_err(|e| e.failure())?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
            } else {
                let input: client::Input = serde_json::from_str(&input).map_err(invalid_request)?;
                let response = runtime
                    .block_on(client::execute(identity, input))
                    .map_err(|e| e.failure())?;
                serde_json::to_string(&response).map_err(|e| e.to_string().into())
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
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_executeOffice(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    request(&mut env, &secret, input, 2)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_manager_nativecore_UnionNative_readOfficeFile(
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
        let state: ::client::office::Snapshot =
            serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let bytes = state
            .domain
            .inner
            .files
            .read(&cid.parse().map_err(|e: ::client::Error| e.to_string())?)
            .map_err(|e| e.to_string())?;
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
