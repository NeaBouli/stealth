//! Utils — Sicherheitshilfsfunktionen
//!
//! Zeroize-Wrapper und Randomness-Funktionen.

use zeroize::Zeroize;
use rand_core::{OsRng, RngCore};

/// Ueberschreibt einen Byte-Buffer sicher mit Nullen.
/// Nutzt die `zeroize`-Crate, die Compiler-Optimierungen verhindert.
pub fn secure_zeroize(buf: &mut [u8]) {
    buf.zeroize();
}

/// Fuellt einen Buffer mit kryptographisch sicherem Zufall.
pub fn random_bytes(buf: &mut [u8]) {
    OsRng.fill_bytes(buf);
}

/// Erzeugt ein zufaelliges Byte-Array fester Groesse.
pub fn random_array<const N: usize>() -> [u8; N] {
    let mut buf = [0u8; N];
    OsRng.fill_bytes(&mut buf);
    buf
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn zeroize_clears_buffer() {
        let mut buf = [0xFFu8; 64];
        secure_zeroize(&mut buf);
        assert_eq!(buf, [0u8; 64]);
    }

    #[test]
    fn random_bytes_not_zero() {
        let mut buf = [0u8; 32];
        random_bytes(&mut buf);
        // Extrem unwahrscheinlich, dass 32 zufaellige Bytes alle 0 sind
        assert_ne!(buf, [0u8; 32]);
    }

    #[test]
    fn random_array_produces_values() {
        let arr: [u8; 16] = random_array();
        assert_ne!(arr, [0u8; 16]);
    }

    #[test]
    fn two_random_arrays_differ() {
        let a: [u8; 32] = random_array();
        let b: [u8; 32] = random_array();
        assert_ne!(a, b);
    }
}
