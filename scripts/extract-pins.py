#!/usr/bin/env python3

"""
Certificate Pin Extractor for UnaMentis Android

Extracts SHA-256 public key pins from provider API certificates.
These pins are used for certificate pinning in OkHttp.

Usage:
    python3 scripts/extract-pins.py

Requirements:
    - Python 3.6+
    - openssl command-line tool
    - Network connectivity to provider APIs
"""

import subprocess
import sys
from typing import Dict, Optional

# ANSI color codes
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
NC = '\033[0m'  # No Color

# Provider APIs to pin
PROVIDERS = [
    ("api.deepgram.com", "Deepgram (STT + TTS)"),
    ("api.assemblyai.com", "AssemblyAI (STT)"),
    ("api.groq.com", "Groq (STT)"),
    ("api.elevenlabs.io", "ElevenLabs (TTS)"),
    ("api.openai.com", "OpenAI (LLM)"),
    ("api.anthropic.com", "Anthropic (LLM)"),
]


def extract_pin(domain: str) -> Optional[str]:
    """
    Extract SHA-256 public key pin from a domain's certificate.

    Args:
        domain: The domain to extract the pin from (e.g., "api.openai.com")

    Returns:
        The pin in format "sha256/BASE64_HASH" or None if extraction failed
    """
    try:
        # Chain of OpenSSL commands to extract the pin
        # 1. Connect and get certificate
        # 2. Extract public key
        # 3. Convert to DER format
        # 4. Compute SHA-256 hash
        # 5. Base64 encode

        cmd = f"""
        echo | openssl s_client -connect {domain}:443 -servername {domain} 2>/dev/null | \
        openssl x509 -pubkey -noout 2>/dev/null | \
        openssl pkey -pubin -outform der 2>/dev/null | \
        openssl dgst -sha256 -binary | \
        openssl enc -base64
        """

        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            timeout=10
        )

        if result.returncode == 0 and result.stdout.strip():
            pin_hash = result.stdout.strip()
            return f"sha256/{pin_hash}"

        return None

    except subprocess.TimeoutExpired:
        print(f"{RED}  ✗ Timeout connecting to {domain}{NC}")
        return None
    except Exception as e:
        print(f"{RED}  ✗ Error: {e}{NC}")
        return None


def main():
    """Main function to extract all pins and generate Kotlin code."""

    print("=" * 50)
    print("Certificate Pin Extractor for UnaMentis")
    print("=" * 50)
    print()

    # Extract pins for all providers
    pins: Dict[str, str] = {}

    for domain, provider_name in PROVIDERS:
        print(f"{YELLOW}Extracting pin for {provider_name} ({domain})...{NC}")

        pin = extract_pin(domain)

        if pin:
            pins[domain] = pin
            print(f"{GREEN}  ✓ Pin: {pin}{NC}")
        else:
            print(f"{RED}  ✗ Failed to extract pin{NC}")
            # Continue anyway, mark as FAILED
            pins[domain] = "FAILED_TO_EXTRACT"

        print()

    # Summary
    print("=" * 50)
    print("Certificate Pins Summary")
    print("=" * 50)
    print()

    for domain, pin in pins.items():
        status = f"{GREEN}✓{NC}" if pin != "FAILED_TO_EXTRACT" else f"{RED}✗{NC}"
        print(f"{status} {domain}: {pin}")

    print()

    # Generate Kotlin code
    print("=" * 50)
    print("Kotlin Code for CertificatePinning.kt")
    print("=" * 50)
    print()

    print("// Update app/src/main/kotlin/com/unamentis/data/remote/CertificatePinning.kt")
    print("// Replace the placeholder pins with these actual pins:")
    print()
    print("val pinner: CertificatePinner by lazy {")
    print("    CertificatePinner.Builder()")

    provider_comments = {
        "api.deepgram.com": "Deepgram (STT + TTS)",
        "api.assemblyai.com": "AssemblyAI (STT)",
        "api.groq.com": "Groq (STT)",
        "api.elevenlabs.io": "ElevenLabs (TTS)",
        "api.openai.com": "OpenAI (LLM)",
        "api.anthropic.com": "Anthropic (LLM)",
    }

    for domain in pins.keys():
        comment = provider_comments.get(domain, domain)
        pin = pins[domain]

        print(f"        // {comment}")
        print(f'        .add("{domain}", "{pin}")')
        print(f'        .add("{domain}", "sha256/BACKUP_PIN_HERE") // Add backup pin')
        print()

    print("        .build()")
    print("}")
    print()

    # Important notes
    print("=" * 50)
    print("Important Notes")
    print("=" * 50)
    print()
    print("1. ALWAYS add at least 2 pins per domain (current + backup)")
    print("2. Get backup pins from provider documentation or cert transparency logs")
    print("3. Monitor certificate expiration dates")
    print("4. Set up alerts for certificate rotation")
    print("5. Update pins BEFORE old certificates expire")
    print()
    print("Certificate Transparency Logs:")
    print("  - https://crt.sh")
    print("  - https://transparencyreport.google.com/https/certificates")
    print()
    print("=" * 50)
    print("Done!")
    print("=" * 50)

    # Check for failures
    failures = [d for d, p in pins.items() if p == "FAILED_TO_EXTRACT"]
    if failures:
        print()
        print(f"{RED}WARNING: Failed to extract pins for:{NC}")
        for domain in failures:
            print(f"  - {domain}")
        print()
        print("Please check your network connection and try again.")
        sys.exit(1)


if __name__ == "__main__":
    main()
