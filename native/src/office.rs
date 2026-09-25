use client::{Error, Identity, Result};
use std::io::Read;
pub async fn execute(
    identity: Identity,
    text: &str,
) -> Result<client::journal::Response<client::office::Office>> {
    let mut value: serde_json::Value = serde_json::from_str(text).map_err(protocol)?;
    if value["action"]["type"] != "local.files.upload" {
        return client::generic::execute(
            identity,
            serde_json::from_value(value).map_err(protocol)?,
        )
        .await;
    }
    #[derive(serde::Deserialize)]
    #[serde(deny_unknown_fields)]
    struct Upload {
        #[serde(rename = "type")]
        _kind: String,
        local_path: String,
        branch: String,
        path: String,
        message: String,
    }
    let upload: Upload = serde_json::from_value(value["action"].take()).map_err(protocol)?;
    let input: client::generic::Input<client::office::Office> =
        serde_json::from_value(value).map_err(protocol)?;
    let mut bytes = Vec::new();
    std::fs::File::open(upload.local_path)?
        .take(events::files::MAX_FILE_BYTES as u64 + 1)
        .read_to_end(&mut bytes)?;
    let peer = input.peer.parse().map_err(protocol)?;
    let address: client::Multiaddr = input.address.parse().map_err(protocol)?;
    if !matches!(address.iter().last(),Some(client::AddressProtocol::P2p(id)) if id==peer) {
        return Err(Error::Denied);
    }
    let node = client::Node::start(identity.clone(), client::NodeConfig::default()).await?;
    let result = async {
        node.dial(address).await?;
        let log = tokio::sync::Mutex::new(input.identity.open()?);
        input.identity.synchronize(&node, &log).await?;
        let trust = log.into_inner();
        client::office::upload(
            &node,
            peer,
            &input.genesis_hash,
            &identity,
            client::office::Upload {
                branch: &upload.branch,
                path: &upload.path,
                message: &upload.message,
                bytes: &bytes,
            },
            &trust,
        )
        .await?;
        let state = client::office::snapshot(&node, peer, &input.genesis_hash).await?;
        let projection = Some(state.identity_presentation(trust.state())?);
        let mut response = client::journal::Response::snapshot(state, &input.genesis_hash)?;
        response.identity_view = projection;
        Ok(response)
    }
    .await;
    node.shutdown().await?;
    result
}
fn protocol(e: impl std::fmt::Display) -> Error {
    Error::Protocol(e.to_string())
}
