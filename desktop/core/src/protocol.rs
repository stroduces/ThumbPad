use anyhow::{bail, Result};

pub const PACKET_SIZE: usize = 32;
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Packet {
    pub kind: u8,
    pub sequence: u64,
    pub micros: u64,
    pub a: f32,
    pub b: f32,
}
impl Packet {
    pub fn decode(bytes: &[u8]) -> Result<Self> {
        if bytes.len() != PACKET_SIZE || &bytes[0..2] != b"TP" || bytes[2] != 1 {
            bail!("unsupported packet");
        }
        let p = Self {
            kind: bytes[3],
            sequence: u64::from_le_bytes(bytes[4..12].try_into()?),
            micros: u64::from_le_bytes(bytes[12..20].try_into()?),
            a: f32::from_le_bytes(bytes[20..24].try_into()?),
            b: f32::from_le_bytes(bytes[24..28].try_into()?),
        };
        if !p.a.is_finite() || !p.b.is_finite() || bytes[28..32] != [0; 4] {
            bail!("invalid payload");
        }
        match p.kind {
            1 | 3 if p.a.abs() <= 2048.0 && p.b.abs() <= 2048.0 => (),
            2 if (0.0..=2.0).contains(&p.a) && p.a.fract() == 0.0 && (p.b == 0.0 || p.b == 1.0) => {
            }
            4 if (0.0..=8.0).contains(&p.a) && p.a.fract() == 0.0 && p.b == 0.0 => (),
            5..=7 if p.a == 0.0 && p.b == 0.0 => (),
            _ => bail!("unknown or invalid event"),
        }
        Ok(p)
    }
    pub fn encode(self) -> [u8; PACKET_SIZE] {
        let mut b = [0; PACKET_SIZE];
        b[..2].copy_from_slice(b"TP");
        b[2] = 1;
        b[3] = self.kind;
        b[4..12].copy_from_slice(&self.sequence.to_le_bytes());
        b[12..20].copy_from_slice(&self.micros.to_le_bytes());
        b[20..24].copy_from_slice(&self.a.to_le_bytes());
        b[24..28].copy_from_slice(&self.b.to_le_bytes());
        b
    }
}
#[derive(Default)]
pub struct Sequence(Option<u64>);
impl Sequence {
    pub fn accept(&mut self, seq: u64) -> bool {
        if self.0.is_some_and(|last| seq <= last) {
            return false;
        }
        self.0 = Some(seq);
        true
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn vector() {
        let p = Packet {
            kind: 1,
            sequence: 1,
            micros: 1000,
            a: 1.5,
            b: -2.0,
        };
        assert_eq!(
            hex::encode(p.encode()),
            "545001010100000000000000e8030000000000000000c03f000000c000000000"
        );
        assert_eq!(Packet::decode(&p.encode()).unwrap(), p);
    }
    #[test]
    fn rejects_invalid() {
        for (kind, a, b) in [
            (0, 0.0, 0.0),
            (2, 3.0, 1.0),
            (2, 0.0, 0.5),
            (1, f32::NAN, 0.0),
            (4, 9.0, 0.0),
            (3, 3000.0, 0.0),
        ] {
            assert!(Packet::decode(
                &Packet {
                    kind,
                    sequence: 1,
                    micros: 0,
                    a,
                    b
                }
                .encode()
            )
            .is_err());
        }
        assert!(Packet::decode(&[0; 31]).is_err());
    }
    #[test]
    fn sequence_replay() {
        let mut s = Sequence::default();
        assert!(s.accept(1));
        assert!(!s.accept(1));
        assert!(!s.accept(0));
        assert!(s.accept(3));
    }
}
