#!/usr/bin/env bash

# Script to extract SHA-256 certificate pins for provider APIs
#
# This script uses OpenSSL to extract the public key hash from each provider's certificate.
# These pins are used for certificate pinning in the Android app.
#
# Usage:
#   ./scripts/extract-certificate-pins.sh
#
# Requirements:
#   - openssl (installed by default on macOS/Linux)
#   - Network connectivity to provider APIs
#   - bash 4.0+ (for associative arrays)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo "Certificate Pin Extractor for UnaMentis"
echo "========================================="
echo ""

# Check bash version (need 4.0+ for associative arrays)
if [ "${BASH_VERSINFO[0]}" -lt 4 ]; then
    echo -e "${RED}Error: This script requires Bash 4.0 or higher${NC}"
    echo "Your version: $BASH_VERSION"
    echo ""
    echo "On macOS, install with: brew install bash"
    echo "Then run with: /usr/local/bin/bash $0"
    exit 1
fi

# Arrays to store domain-pin pairs (using parallel arrays for bash 3 compatibility fallback)
domains=()
pins_values=()

# Function to extract certificate pin for a domain
extract_pin() {
    local domain=$1
    local provider=$2

    echo -e "${YELLOW}Extracting pin for $provider ($domain)...${NC}"

    # Extract certificate and compute SHA-256 hash of public key
    local pin=$(echo | openssl s_client -connect "$domain:443" -servername "$domain" 2>/dev/null | \
        openssl x509 -pubkey -noout 2>/dev/null | \
        openssl pkey -pubin -outform der 2>/dev/null | \
        openssl dgst -sha256 -binary | \
        openssl enc -base64)

    if [ -z "$pin" ]; then
        echo -e "${RED}  ✗ Failed to extract pin${NC}"
        return 1
    fi

    echo -e "${GREEN}  ✓ Pin: sha256/$pin${NC}"
    echo ""

    # Store in parallel arrays
    domains+=("$domain")
    pins_values+=("sha256/$pin")
}

# Extract pins for all provider APIs
extract_pin "api.deepgram.com" "Deepgram (STT + TTS)"
extract_pin "api.assemblyai.com" "AssemblyAI (STT)"
extract_pin "api.groq.com" "Groq (STT)"
extract_pin "api.elevenlabs.io" "ElevenLabs (TTS)"
extract_pin "api.openai.com" "OpenAI (LLM)"
extract_pin "api.anthropic.com" "Anthropic (LLM)"

echo "========================================="
echo "Certificate Pins Summary"
echo "========================================="
echo ""

# Print all extracted pins
for i in "${!domains[@]}"; do
    echo "${domains[$i]}: ${pins_values[$i]}"
done

echo ""
echo "========================================="
echo "Kotlin Code for CertificatePinning.kt"
echo "========================================="
echo ""

# Generate Kotlin code snippet
cat <<EOF
// Update app/src/main/kotlin/com/unamentis/data/remote/CertificatePinning.kt
// Replace the placeholder pins (AAAA..., BBBB..., etc.) with these actual pins:

val pinner: CertificatePinner by lazy {
    CertificatePinner.Builder()
        // Deepgram (STT + TTS)
        .add("api.deepgram.com", "${pins_values[0]}")
        .add("api.deepgram.com", "sha256/BACKUP_PIN_HERE") // Add backup pin from provider docs

        // AssemblyAI (STT)
        .add("api.assemblyai.com", "${pins_values[1]}")
        .add("api.assemblyai.com", "sha256/BACKUP_PIN_HERE")

        // Groq (STT)
        .add("api.groq.com", "${pins_values[2]}")
        .add("api.groq.com", "sha256/BACKUP_PIN_HERE")

        // ElevenLabs (TTS)
        .add("api.elevenlabs.io", "${pins_values[3]}")
        .add("api.elevenlabs.io", "sha256/BACKUP_PIN_HERE")

        // OpenAI (LLM)
        .add("api.openai.com", "${pins_values[4]}")
        .add("api.openai.com", "sha256/BACKUP_PIN_HERE")

        // Anthropic (LLM)
        .add("api.anthropic.com", "${pins_values[5]}")
        .add("api.anthropic.com", "sha256/BACKUP_PIN_HERE")

        .build()
}
EOF

echo ""
echo "========================================="
echo "Important Notes"
echo "========================================="
echo ""
echo "1. ALWAYS add at least 2 pins per domain (current + backup)"
echo "2. Get backup pins from provider documentation or cert transparency logs"
echo "3. Monitor certificate expiration dates"
echo "4. Set up alerts for certificate rotation"
echo "5. Update pins BEFORE old certificates expire"
echo ""
echo "Certificate Transparency Logs:"
echo "  - https://crt.sh"
echo "  - https://transparencyreport.google.com/https/certificates"
echo ""
echo "========================================="
echo "Done!"
echo "========================================="
