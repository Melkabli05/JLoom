#!/usr/bin/env bash
# jloom installer — clones the repo, builds the CLI, and prints a PATH hint.
# Works on Linux and macOS with bash, git, and a JDK 25+ install.

set -euo pipefail

REPO_URL="${JLOOM_REPO:-https://github.com/Melkabli05/JLoom.git}"
INSTALL_DIR="${JLOOM_INSTALL_DIR:-$HOME/.jloom}"
BIN_DIR="$INSTALL_DIR/bin"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()    { printf "${BLUE}==>${NC} %s\n" "$*"; }
success() { printf "${GREEN}==✓${NC} %s\n" "$*"; }
warn()    { printf "${YELLOW}==!${NC} %s\n" "$*"; }
fail()    { printf "${RED}==✗${NC} %s\n" "$*" >&2; exit 1; }

# 1. Verify prerequisites

info "Checking prerequisites"

command -v java >/dev/null 2>&1 || fail "java not found. Install JDK 25+ from https://adoptium.net/ and re-run."
command -v git  >/dev/null 2>&1 || fail "git not found. Install git and re-run."

JAVA_MAJOR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{ if ($1 == 1) print $2; else print $1 }')
if [ "${JAVA_MAJOR:-0}" -lt 25 ]; then
    fail "Java 25+ required (found $(java -version 2>&1 | head -1)). Install JDK 25+ from https://adoptium.net/ and re-run."
fi
success "Java $(java -version 2>&1 | awk -F '"' '/version/ {print $2}') and git found"

# 2. Clone (or update) the repo

if [ -d "$INSTALL_DIR/.git" ]; then
    info "Existing install at $INSTALL_DIR — pulling latest"
    git -C "$INSTALL_DIR" pull --ff-only || fail "git pull failed in $INSTALL_DIR"
else
    info "Cloning $REPO_URL to $INSTALL_DIR"
    mkdir -p "$(dirname "$INSTALL_DIR")"
    git clone --depth 1 "$REPO_URL" "$INSTALL_DIR"
fi

# 3. Build

info "Building jloom (this takes a few minutes the first time)"
cd "$INSTALL_DIR"
./gradlew installDist --no-daemon

# 4. Symlink `jloom` into PATH if a writable bin dir exists

if [ -d "$HOME/.local/bin" ] && [ -w "$HOME/.local/bin" ]; then
    ln -sf "$BIN_DIR/jloom" "$HOME/.local/bin/jloom"
    success "Linked $HOME/.local/bin/jloom -> $BIN_DIR/jloom"
    PATH_OK=1
elif [ -d "/usr/local/bin" ] && [ -w "/usr/local/bin" ]; then
    ln -sf "$BIN_DIR/jloom" "/usr/local/bin/jloom"
    success "Linked /usr/local/bin/jloom -> $BIN_DIR/jloom"
    PATH_OK=1
else
    PATH_OK=0
fi

# 5. Done

echo
success "jloom installed at $INSTALL_DIR"
echo
if [ "$PATH_OK" = "1" ]; then
    info "Run ${GREEN}jloom list${NC} to verify — no PATH setup needed."
else
    info "To use jloom, add it to your PATH (one-time setup):"
    echo
    echo "  ${YELLOW}# for this shell only${NC}"
    echo "  export PATH=\"$BIN_DIR:\$PATH\""
    echo
    echo "  ${YELLOW}# for all future shells (add to ~/.bashrc or ~/.zshrc)${NC}"
    echo "  echo 'export PATH=\"$BIN_DIR:\$PATH\"' >> ~/.bashrc"
    echo
    info "Then run ${GREEN}jloom list${NC} to verify."
fi
echo
info "Update later with:  ${GREEN}cd $INSTALL_DIR && git pull && ./gradlew installDist${NC}"
echo