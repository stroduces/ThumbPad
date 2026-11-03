use thumbpad_core::Receiver;
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Explicitly isolated loopback, volatile identity, no OS injection. Never a production auth bypass.
    let r = Receiver::new(true, true, Some("127.0.0.1".parse()?), 47322)?;
    println!(
        "{}",
        serde_json::json!({"snapshot":r.snapshot(),"certificate":hex::encode(r.certificate_der())})
    );
    let control = r.clone();
    tokio::spawn(async move {
        use tokio::io::{AsyncBufReadExt, BufReader};
        let mut lines = BufReader::new(tokio::io::stdin()).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            match line.trim() {
                "revoke" => {
                    for d in control.snapshot().devices {
                        let _ = control.revoke(&d.id);
                    }
                }
                "disable" => control.set_enabled(false),
                "enable" => control.set_enabled(true),
                _ => (),
            }
        }
    });
    let approve = r.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
            for p in approve.snapshot().pending {
                approve.approve(&p.id, true).unwrap();
            }
        }
    });
    r.serve().await
}
