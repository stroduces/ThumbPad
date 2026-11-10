pub mod input;
mod keyboard;
pub mod protocol;
#[cfg(any(target_os = "macos", test))]
mod scroll;
use aes_gcm::{aead::Aead, Aes256Gcm, KeyInit, Nonce};
use anyhow::{anyhow, bail, Context, Result};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    net::{IpAddr, Ipv4Addr},
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        Arc, Mutex,
    },
    time::Instant,
};
use subtle::ConstantTimeEq;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpListener,
    time::{timeout, Duration},
};
use tokio_rustls::{
    rustls::{
        self,
        pki_types::{CertificateDer, PrivatePkcs8KeyDer},
    },
    TlsAcceptor,
};

pub const PORT: u16 = 47321;
fn secret() -> String {
    let mut b = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut b);
    hex::encode(b)
}
fn hash(s: &str) -> String {
    hex::encode(Sha256::digest(s.as_bytes()))
}
fn equal(a: &str, b: &str) -> bool {
    a.as_bytes().ct_eq(b.as_bytes()).into()
}
#[derive(Clone, Serialize, Deserialize)]
pub struct Trusted {
    pub id: String,
    pub name: String,
    pub token_hash: String,
}
#[derive(Serialize, Deserialize)]
struct Vault {
    cert: Vec<u8>,
    key: Vec<u8>,
    devices: BTreeMap<String, Trusted>,
}
#[derive(Clone, Serialize)]
pub struct Pending {
    pub id: String,
    pub name: String,
    pub code: String,
}
struct Request {
    pending: Pending,
    approved: Option<bool>,
    created: Instant,
}
struct Pairing {
    secret: String,
    created: Instant,
}
#[derive(Clone, Serialize)]
pub struct Snapshot {
    pub host: String,
    pub port: u16,
    pub fingerprint: String,
    pub pairing_uri: String,
    pub pairing_code: String,
    pub enabled: bool,
    pub connected: Option<String>,
    pub devices: Vec<TrustedView>,
    pub pending: Vec<Pending>,
    pub accessibility: bool,
    pub packets: u64,
    pub dispatch_median_us: u64,
    pub dispatch_p95_us: u64,
    pub error: Option<String>,
}
#[derive(Clone, Serialize)]
pub struct TrustedView {
    pub id: String,
    pub name: String,
}
pub struct Receiver {
    vault: Mutex<Vault>,
    requests: Mutex<BTreeMap<String, Request>>,
    pairing: Mutex<Pairing>,
    pub enabled: AtomicBool,
    active: AtomicBool,
    epoch: AtomicU64,
    connected: Mutex<Option<String>>,
    host: IpAddr,
    port: u16,
    packets: AtomicU64,
    samples: Mutex<Vec<u64>>,
    error: Mutex<Option<String>>,
    dry: bool,
    storage: Option<([u8; 32], std::path::PathBuf)>,
}
impl Receiver {
    pub fn certificate_der(&self) -> Vec<u8> {
        self.vault.lock().unwrap().cert.clone()
    }
    pub fn new(dry: bool, volatile: bool, host: Option<IpAddr>, port: u16) -> Result<Arc<Self>> {
        let host =
            host.unwrap_or(local_ip_address::local_ip().unwrap_or(IpAddr::V4(Ipv4Addr::LOCALHOST)));
        if !private(host) {
            bail!("Choose a private LAN IPv4 address; public interface binding is refused");
        }
        let storage = if volatile {
            None
        } else {
            let path = dirs::data_local_dir()
                .ok_or_else(|| anyhow!("No local app data directory"))?
                .join("ThumbPad")
                .join("vault.bin");
            let entry = keyring::Entry::new("ThumbPad", "receiver-key-v1")?;
            let key = match entry.get_secret() {
                Ok(v) => v
                    .try_into()
                    .map_err(|_| anyhow!("Invalid secure storage key"))?,
                Err(keyring::Error::NoEntry) => {
                    if path.exists() {
                        bail!("Vault key missing; restore secure storage before reconnecting");
                    }
                    let mut key = [0; 32];
                    rand::thread_rng().fill_bytes(&mut key);
                    entry.set_secret(&key)?;
                    key
                }
                Err(e) => return Err(e.into()),
            };
            Some((key, path))
        };
        let stored = if let Some((key, path)) = &storage {
            if path.exists() {
                let data = std::fs::read(path)?;
                if data.len() < 28 {
                    bail!("Invalid vault");
                }
                let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| anyhow!("vault key"))?;
                Some(
                    cipher
                        .decrypt(Nonce::from_slice(&data[..12]), &data[12..])
                        .map_err(|_| anyhow!("Vault authentication failed"))?,
                )
            } else {
                None
            }
        } else {
            None
        };
        let vault = if let Some(s) = stored {
            serde_json::from_slice(&s)
                .context("Invalid secure-store state; do not silently replace identity")?
        } else {
            let cert = rcgen::generate_simple_self_signed(vec!["thumbpad.local".into()])?;
            Vault {
                cert: cert.cert.der().to_vec(),
                key: cert.key_pair.serialize_der(),
                devices: BTreeMap::new(),
            }
        };
        let s = Arc::new(Self {
            vault: Mutex::new(vault),
            requests: Mutex::new(BTreeMap::new()),
            pairing: Mutex::new(Pairing {
                secret: secret(),
                created: Instant::now(),
            }),
            enabled: AtomicBool::new(true),
            active: AtomicBool::new(false),
            epoch: AtomicU64::new(0),
            connected: Mutex::new(None),
            host,
            port,
            packets: AtomicU64::new(0),
            samples: Mutex::new(Vec::new()),
            error: Mutex::new(None),
            dry,
            storage,
        });
        s.persist()?;
        Ok(s)
    }
    fn persist(&self) -> Result<()> {
        if let Some((key, path)) = &self.storage {
            let mut nonce = [0; 12];
            rand::thread_rng().fill_bytes(&mut nonce);
            let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| anyhow!("vault key"))?;
            let data = serde_json::to_vec(&*self.vault.lock().unwrap())?;
            let encrypted = cipher
                .encrypt(Nonce::from_slice(&nonce), data.as_slice())
                .map_err(|_| anyhow!("vault encryption failed"))?;
            std::fs::create_dir_all(path.parent().unwrap())?;
            let pending = path.with_extension("pending");
            std::fs::write(&pending, [nonce.to_vec(), encrypted].concat())?;
            std::fs::rename(pending, path)?;
        }
        Ok(())
    }
    pub fn snapshot(&self) -> Snapshot {
        let vault = self.vault.lock().unwrap();
        let mut pair = self.pairing.lock().unwrap();
        if pair.created.elapsed() > Duration::from_secs(120) {
            *pair = Pairing {
                secret: secret(),
                created: Instant::now(),
            };
        }
        let fp = hex::encode(Sha256::digest(&vault.cert));
        let mut samples = self.samples.lock().unwrap().clone();
        samples.sort_unstable();
        let q = |p: usize| {
            if samples.is_empty() {
                0
            } else {
                samples[((samples.len() - 1) * p) / 100]
            }
        };
        Snapshot {
            host: self.host.to_string(),
            port: self.port,
            fingerprint: fp.clone(),
            pairing_uri: format!(
                "thumbpad://pair?host={}&port={}&fp={}&secret={}",
                self.host, self.port, fp, pair.secret
            ),
            pairing_code: pair.secret[..6].to_uppercase(),
            enabled: self.enabled.load(Ordering::SeqCst),
            connected: self.connected.lock().unwrap().clone(),
            devices: vault
                .devices
                .values()
                .map(|d| TrustedView {
                    id: d.id.clone(),
                    name: d.name.clone(),
                })
                .collect(),
            pending: self
                .requests
                .lock()
                .unwrap()
                .values()
                .filter(|r| r.approved.is_none() && r.created.elapsed() < Duration::from_secs(60))
                .map(|r| r.pending.clone())
                .collect(),
            accessibility: input::accessibility(),
            packets: self.packets.load(Ordering::Relaxed),
            dispatch_median_us: q(50),
            dispatch_p95_us: q(95),
            error: self.error.lock().unwrap().clone(),
        }
    }
    pub fn approve(&self, id: &str, allow: bool) -> Result<()> {
        let mut requests = self.requests.lock().unwrap();
        let r = requests
            .get_mut(id)
            .ok_or_else(|| anyhow!("Pairing request expired"))?;
        if r.created.elapsed() > Duration::from_secs(60) {
            bail!("expired");
        }
        r.approved = Some(allow);
        Ok(())
    }
    pub fn revoke(&self, id: &str) -> Result<()> {
        self.vault.lock().unwrap().devices.remove(id);
        self.epoch.fetch_add(1, Ordering::SeqCst);
        self.persist()
    }
    pub fn set_enabled(&self, value: bool) {
        self.enabled.store(value, Ordering::SeqCst);
        self.epoch.fetch_add(1, Ordering::SeqCst);
    }
    pub fn disconnect(&self) {
        self.epoch.fetch_add(1, Ordering::SeqCst);
    }
    pub fn rotate(&self) {
        *self.pairing.lock().unwrap() = Pairing {
            secret: secret(),
            created: Instant::now(),
        };
    }
    pub async fn serve(self: Arc<Self>) -> Result<()> {
        let _ = rustls::crypto::ring::default_provider().install_default();
        let config = {
            let v = self.vault.lock().unwrap();
            rustls::ServerConfig::builder()
                .with_no_client_auth()
                .with_single_cert(
                    vec![CertificateDer::from(v.cert.clone())],
                    PrivatePkcs8KeyDer::from(v.key.clone()).into(),
                )?
        };
        let tls = TlsAcceptor::from(Arc::new(config));
        let listener = TcpListener::bind((self.host, self.port)).await?;
        let _mdns = if self.host.is_loopback() {
            None
        } else {
            let daemon = mdns_sd::ServiceDaemon::new()?;
            let properties = [("v", "1")];
            let info = mdns_sd::ServiceInfo::new(
                "_thumbpad._tcp.local.",
                "ThumbPad",
                "thumbpad.local.",
                self.host.to_string().as_str(),
                self.port,
                &properties[..],
            )?;
            daemon.register(info)?;
            Some(daemon)
        };
        let semaphore = Arc::new(tokio::sync::Semaphore::new(8));
        loop {
            let (stream, peer) = listener.accept().await?;
            if !private(peer.ip()) {
                continue;
            }
            let Ok(permit) = semaphore.clone().try_acquire_owned() else {
                continue;
            };
            stream.set_nodelay(true)?;
            let this = self.clone();
            let tls = tls.clone();
            tokio::spawn(async move {
                let _permit = permit;
                let work = async {
                    let mut s = timeout(Duration::from_secs(5), tls.accept(stream)).await??;
                    let mut len = [0; 2];
                    timeout(Duration::from_secs(5), s.read_exact(&mut len)).await??;
                    let n = u16::from_le_bytes(len) as usize;
                    if n == 0 || n > 2048 {
                        bail!("invalid hello length");
                    }
                    let mut b = vec![0; n];
                    timeout(Duration::from_secs(5), s.read_exact(&mut b)).await??;
                    let hello: Hello = serde_json::from_slice(&b)?;
                    this.session(&mut s, hello).await
                };
                if let Err(e) = work.await {
                    *this.error.lock().unwrap() = Some(format!("Connection ended: {e}"));
                }
            });
        }
    }
    async fn session(
        &self,
        s: &mut tokio_rustls::server::TlsStream<tokio::net::TcpStream>,
        h: Hello,
    ) -> Result<()> {
        if h.version != 1
            || h.id.len() != 32
            || !h.id.bytes().all(|x| x.is_ascii_hexdigit())
            || h.name.is_empty()
            || h.name.len() > 80
        {
            bail!("invalid device identity");
        }
        if !self.enabled.load(Ordering::SeqCst) {
            bail!("receiver disabled");
        }
        let token = if let Some(token) = h.token {
            let valid = self
                .vault
                .lock()
                .unwrap()
                .devices
                .get(&h.id)
                .is_some_and(|d| equal(&d.token_hash, &hash(&token)));
            if !valid {
                bail!("untrusted device");
            }
            None
        } else {
            {
                let pair = self.pairing.lock().unwrap();
                if pair.created.elapsed() > Duration::from_secs(120)
                    || !equal(&pair.secret, h.secret.as_deref().unwrap_or(""))
                {
                    bail!("invalid or expired pairing invitation");
                }
            }
            let code = self.pairing.lock().unwrap().secret[..6].to_uppercase();
            {
                let mut requests = self.requests.lock().unwrap();
                requests.retain(|_, r| r.created.elapsed() < Duration::from_secs(60));
                if requests.len() >= 4 {
                    bail!("too many pairing requests");
                }
                if requests.contains_key(&h.id) {
                    bail!("pairing already pending");
                }
                requests.insert(
                    h.id.clone(),
                    Request {
                        pending: Pending {
                            id: h.id.clone(),
                            name: h.name.clone(),
                            code,
                        },
                        approved: None,
                        created: Instant::now(),
                    },
                );
            }
            let mut allowed = false;
            for _ in 0..120 {
                tokio::time::sleep(Duration::from_millis(500)).await;
                let decision = self
                    .requests
                    .lock()
                    .unwrap()
                    .get(&h.id)
                    .and_then(|r| r.approved);
                if let Some(v) = decision {
                    allowed = v;
                    break;
                }
            }
            self.requests.lock().unwrap().remove(&h.id);
            if !allowed || !self.enabled.load(Ordering::SeqCst) {
                bail!("pairing denied or timed out");
            }
            let token = secret();
            self.vault.lock().unwrap().devices.insert(
                h.id.clone(),
                Trusted {
                    id: h.id.clone(),
                    name: h.name.clone(),
                    token_hash: hash(&token),
                },
            );
            self.persist()?;
            self.rotate();
            Some(token)
        };
        if self
            .active
            .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
            .is_err()
        {
            bail!("another remote is active");
        }
        struct Active<'a>(&'a Receiver);
        impl Drop for Active<'_> {
            fn drop(&mut self) {
                self.0.active.store(false, Ordering::SeqCst);
                *self.0.connected.lock().unwrap() = None;
            }
        }
        let _active = Active(self);
        *self.connected.lock().unwrap() = Some(h.name);
        let epoch = self.epoch.load(Ordering::SeqCst);
        let reply = serde_json::to_vec(
            &serde_json::json!({"ok":true,"token":token,"version":1,"key_remapping":true}),
        )?;
        timeout(Duration::from_secs(3), async {
            s.write_all(&(reply.len() as u16).to_le_bytes()).await?;
            s.write_all(&reply).await
        })
        .await??;
        let mut input = input::Input::new(self.dry);
        let mut sequence = protocol::Sequence::default();
        let mut tick = tokio::time::interval(Duration::from_millis(50));
        let mut last = Instant::now();
        let mut buffer = [0; 32];
        let mut offset = 0;
        loop {
            tokio::select! {
                _=tick.tick()=>{if self.epoch.load(Ordering::SeqCst)!=epoch||!self.enabled.load(Ordering::SeqCst)||last.elapsed()>Duration::from_secs(3){bail!("disconnected or heartbeat expired");}},
                n=s.read(&mut buffer[offset..])=>{let n=n?;if n==0{bail!("peer closed");}offset+=n;if offset!=32{continue;}offset=0;
                    let start=Instant::now();let p=protocol::Packet::decode(&buffer)?;if !sequence.accept(p.sequence){bail!("out of order event");}last=Instant::now();
                    input.dispatch(p)?;
                    if p.kind==5 {timeout(Duration::from_secs(1),s.write_all(&buffer)).await??;}
                    self.packets.fetch_add(1,Ordering::Relaxed);let mut samples=self.samples.lock().unwrap();if samples.len()>=2048{samples.remove(0);}samples.push(start.elapsed().as_micros() as u64);
                }
            }
        }
    }
}
#[derive(Deserialize)]
struct Hello {
    version: u8,
    id: String,
    name: String,
    token: Option<String>,
    secret: Option<String>,
}
pub fn private(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(v) => v.is_private() || v.is_loopback() || v.is_link_local(),
        IpAddr::V6(_) => false,
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn restrict_public() {
        assert!(private("192.168.1.2".parse().unwrap()));
        assert!(!private("8.8.8.8".parse().unwrap()));
        assert!(!private("0.0.0.0".parse().unwrap()));
    }
    #[test]
    fn invitation_rotates_and_revocation_invalidates() {
        let r = Receiver::new(true, true, Some("127.0.0.1".parse().unwrap()), 0).unwrap();
        let old = r.snapshot();
        r.rotate();
        assert_ne!(old.pairing_uri, r.snapshot().pairing_uri);
        r.revoke("absent").unwrap();
        assert_eq!(r.epoch.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn token_comparison() {
        assert!(equal(&hash("abc"), &hash("abc")));
        assert!(!equal(&hash("abc"), &hash("abd")));
    }
}
