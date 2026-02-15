//! Identity-Modul — X25519 Key Exchange
//!
//! Erzeugt langlebige Identitaetsschluessel (X25519) und
//! berechnet Diffie-Hellman Shared Secrets.

use x25519_dalek::{PublicKey, StaticSecret};
use rand_core::OsRng;
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Ein X25519-Schluesselpaar (privat + oeffentlich).
pub struct IdentityKeyPair {
    secret: StaticSecret,
    public: PublicKey,
}

/// Ergebnis eines Diffie-Hellman Key-Exchange (32 Byte Shared Secret).
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct SharedSecret {
    bytes: [u8; 32],
}

impl IdentityKeyPair {
    /// Erzeugt ein neues zufaelliges X25519-Schluesselpaar.
    pub fn generate() -> Self {
        let secret = StaticSecret::random_from_rng(OsRng);
        let public = PublicKey::from(&secret);
        Self { secret, public }
    }

    /// Gibt den oeffentlichen Schluessel zurueck (32 Byte).
    pub fn public_key(&self) -> &PublicKey {
        &self.public
    }

    /// Gibt den oeffentlichen Schluessel als Byte-Array zurueck.
    pub fn public_key_bytes(&self) -> [u8; 32] {
        self.public.to_bytes()
    }

    /// Berechnet das Shared Secret mit dem oeffentlichen Schluessel des Peers.
    pub fn diffie_hellman(&self, peer_public: &PublicKey) -> SharedSecret {
        let raw = self.secret.diffie_hellman(peer_public);
        SharedSecret {
            bytes: raw.to_bytes(),
        }
    }
}

impl Drop for IdentityKeyPair {
    fn drop(&mut self) {
        // Overwrite secret key with zeros on drop.
        // StaticSecret stores key as [u8; 32] internally.
        let zero_secret = StaticSecret::from([0u8; 32]);
        self.secret = zero_secret;
    }
}

impl SharedSecret {
    /// Gibt das rohe Shared Secret als Byte-Array zurueck.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.bytes
    }
}

/// Erzeugt ein neues Schluesselpaar (Alias fuer IdentityKeyPair::generate).
pub fn generate_identity_keypair() -> IdentityKeyPair {
    IdentityKeyPair::generate()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keypair_generation() {
        let kp = IdentityKeyPair::generate();
        let pub_bytes = kp.public_key_bytes();
        assert_ne!(pub_bytes, [0u8; 32]);
    }

    #[test]
    fn two_keypairs_are_different() {
        let kp1 = IdentityKeyPair::generate();
        let kp2 = IdentityKeyPair::generate();
        assert_ne!(kp1.public_key_bytes(), kp2.public_key_bytes());
    }

    #[test]
    fn diffie_hellman_shared_secret() {
        let alice = IdentityKeyPair::generate();
        let bob = IdentityKeyPair::generate();

        let secret_alice = alice.diffie_hellman(bob.public_key());
        let secret_bob = bob.diffie_hellman(alice.public_key());

        // Beide Seiten muessen das gleiche Shared Secret berechnen
        assert_eq!(secret_alice.as_bytes(), secret_bob.as_bytes());
    }

    #[test]
    fn shared_secret_not_zero() {
        let alice = IdentityKeyPair::generate();
        let bob = IdentityKeyPair::generate();

        let secret = alice.diffie_hellman(bob.public_key());
        assert_ne!(secret.as_bytes(), &[0u8; 32]);
    }

    #[test]
    fn different_peers_different_secrets() {
        let alice = IdentityKeyPair::generate();
        let bob = IdentityKeyPair::generate();
        let charlie = IdentityKeyPair::generate();

        let secret_ab = alice.diffie_hellman(bob.public_key());
        let secret_ac = alice.diffie_hellman(charlie.public_key());

        assert_ne!(secret_ab.as_bytes(), secret_ac.as_bytes());
    }
}
