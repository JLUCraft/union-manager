use client::{Error, Identity, Result};
use std::io::Read;
const MAX_APPLICATION_TEXT_BYTES: usize = 16 * 1024;
#[derive(serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct Application {
    #[serde(rename = "type")]
    _kind: String,
    id: String,
    title: String,
    description: String,
    attachment: Option<String>,
    window: events::Window,
}
impl Application {
    fn document(&self) -> Result<Vec<u8>> {
        if self.description.trim().is_empty()
            || self.description.len() > MAX_APPLICATION_TEXT_BYTES
            || self.title.trim().is_empty()
            || self.title.len() > 256
            || self.id.is_empty()
            || self.id.len() > 128
            || self.window.opens_ms >= self.window.closes_ms
            || self.window.closes_ms > self.window.starts_ms
        {
            return Err(Error::Denied);
        }
        serde_json::to_vec(
            &serde_json::json!({"description":self.description,"attachment":self.attachment}),
        )
        .map_err(protocol)
    }
}
#[derive(serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct Review {
    #[serde(rename = "type")]
    kind: String,
    instance: String,
    comment: String,
}
pub async fn execute(
    identity: Identity,
    text: &str,
) -> Result<client::journal::Response<client::application::Application>> {
    let mut value: serde_json::Value = serde_json::from_str(text).map_err(protocol)?;
    if value["action"]["type"] == "local.points.request_ai" {
        return client::points::request_text(identity, value).await;
    }
    let creating = value["action"]["type"] == "local.events.create";
    let amending = value["action"]["type"] == "local.events.amend";
    let reviewing = matches!(
        value["action"]["type"].as_str(),
        Some("local.flows.approve" | "local.flows.reject")
    );
    if !creating && !amending && !reviewing && value["action"]["type"] != "local.files.upload" {
        return client::generic::execute(
            identity,
            serde_json::from_value(value).map_err(protocol)?,
            client::journal::PROTOCOL,
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
    let application: Option<Application> = if creating || amending {
        Some(serde_json::from_value(value["action"].take()).map_err(protocol)?)
    } else {
        None
    };
    let review: Option<Review> = if reviewing {
        let review: Review = serde_json::from_value(value["action"].take()).map_err(protocol)?;
        if review.comment.trim().is_empty() || review.comment.len() > MAX_APPLICATION_TEXT_BYTES {
            return Err(Error::Denied);
        }
        Some(review)
    } else {
        None
    };
    let upload: Upload = if let Some(application) = &application {
        Upload {
            _kind: "local.events.create".into(),
            local_path: String::new(),
            branch: format!("request-{}", application.id),
            path: "application.json".into(),
            message: "application".into(),
        }
    } else if let Some(review) = &review {
        Upload {
            _kind: review.kind.clone(),
            local_path: String::new(),
            branch: client::protocol::digest(
                format!("review:{}:{}", review.instance, identity.peer_id()).as_bytes(),
            ),
            path: client::protocol::digest(review.comment.as_bytes()),
            message: "review".into(),
        }
    } else {
        serde_json::from_value(value["action"].take()).map_err(protocol)?
    };
    let input: client::generic::Input<client::application::Application> =
        serde_json::from_value(value).map_err(protocol)?;
    let bytes = if let Some(application) = &application {
        application.document()?
    } else if let Some(review) = &review {
        review.comment.as_bytes().to_vec()
    } else {
        let mut bytes = Vec::new();
        std::fs::File::open(&upload.local_path)?
            .take(events::files::MAX_FILE_BYTES as u64 + 1)
            .read_to_end(&mut bytes)?;
        bytes
    };
    let (address, peer) = client::endpoint(&input.address, &input.peer).map_err(protocol)?;
    let node = client::Node::start(identity.clone(), client::NodeConfig::default()).await?;
    let result = async {
        node.dial(address).await?;
        let log = tokio::sync::Mutex::new(input.identity.open()?);
        input.identity.synchronize(&node, &log).await?;
        let trust = log.into_inner();
        if let Some(attachment) = application.as_ref().and_then(|a| a.attachment.as_ref()) {
            let cid = attachment.parse::<client::protocol::Cid>().map_err(protocol)?;
            client::files::snapshot(&node, peer, &input.genesis_hash).await?.domain.inner.files.read(&cid).map_err(protocol)?;
        }
        let document = client::files::upload(
            &node,
            peer,
            &input.genesis_hash,
            &identity,
            client::files::Upload {
                branch: &upload.branch,
                path: &upload.path,
                message: &upload.message,
                bytes: &bytes,
            },
            &trust,
        )
        .await?;
        if let Some(review) = review {
            let digest = client::protocol::digest(&bytes);
            let value = if review.kind == "local.flows.approve" {
                serde_json::json!({"type":"flows.approve","instance":review.instance,"comment_hash":digest})
            } else { serde_json::json!({"type":"flows.reject","instance":review.instance,"reason":digest}) };
            let action = serde_json::from_value(value).map_err(protocol)?;
            return client::trust::execute::<client::application::Application>(&node, peer, &input.genesis_hash, &identity, &trust, action, client::journal::PROTOCOL).await;
        }
        if let Some(application) = application {
            let value = if amending {
                serde_json::json!({"type":"events.revise","id":application.id,"title":application.title,"brief_hash":document.to_string(),"window":application.window})
            } else {
                serde_json::json!({"type":"events.create","id":application.id,"title":application.title,"brief_hash":document.to_string(),"window":application.window})
            };
            let action = serde_json::from_value(value).map_err(protocol)?;
            return client::trust::execute::<client::application::Application>(&node, peer, &input.genesis_hash, &identity, &trust, action, client::journal::PROTOCOL).await;
        }
        let mut response = client::journal::request::<client::application::Application>(&node, peer, &input.genesis_hash, client::journal::Request::Snapshot, client::journal::PROTOCOL).await?;
        if let Some(state)=&response.state {response.identity_view=Some(state.identity_presentation(trust.state())?);}
        Ok(response)
    }
    .await;
    node.shutdown().await?;
    result
}
fn protocol(e: impl std::fmt::Display) -> Error {
    Error::Protocol(e.to_string())
}

pub async fn push(
    identity: Identity,
    text: &str,
) -> Result<client::protocol::ApplicationPushResponse> {
    #[derive(serde::Deserialize)]
    #[serde(deny_unknown_fields)]
    struct Input {
        address: String,
        peer: String,
        request: client::protocol::ApplicationPushRequest,
    }
    let input: Input = serde_json::from_str(text).map_err(protocol)?;
    let (address, peer) = client::endpoint(&input.address, &input.peer).map_err(protocol)?;
    let node = client::Node::start(identity, client::NodeConfig::default()).await?;
    let result = async {
        node.dial(address).await?;
        client::files::push(&node, peer, &input.request).await
    }
    .await;
    node.shutdown().await?;
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn application_accepts_text_without_attachment_and_rejects_invalid_windows() {
        let mut application = Application {
            _kind: "local.events.create".into(),
            id: "request-one".into(),
            title: "Application".into(),
            description: "Description".into(),
            attachment: None,
            window: events::Window {
                opens_ms: 1,
                closes_ms: 2,
                starts_ms: 3,
            },
        };
        let document = application.document();
        assert!(document.is_ok());
        application.window.closes_ms = 1;
        assert!(application.document().is_err());
        application.window.closes_ms = 2;
        application.description = " ".into();
        assert!(application.document().is_err());
    }
}
