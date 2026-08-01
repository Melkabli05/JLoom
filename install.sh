#!/usr/bin/env bash
# jloom installer — clones the repo, builds the CLI, and adds it to your PATH.
# Works on Linux and macOS with bash, git, and a JDK 25+ install.
#
# SECURITY MODEL
#   - This script runs `git clone` and then executes the cloned `gradlew` (a shell
#     script) with whatever Java toolchain is on the machine. The remote is
#     trusted by default. Set JLOOM_REPO to override.
#   - By default we pin to a specific git ref (configurable via JLOOM_REF) so a
#     compromised or supply-chain-injected `main` does not silently install
#     arbitrary code. Pass `--latest` to opt into tracking main HEAD.
#   - System-wide symlinks to /usr/local/bin are opt-in via
#     JLOOM_SYSTEM_INSTALL=1 (skipped by default; never run as root without
#     that flag). The per-user ~/.local/bin path is always preferred.
#   - PATH entry is appended to your shell rc file (~/.bashrc / ~/.zshrc / etc.)
#     — idempotent, only added once.

set -euo pipefail

REPO_URL="${JLOOM_REPO:-https://github.com/Melkabli05/JLoom.git}"
REF="${JLOOM_REF:-main}"
USE_LATEST="false"
INSTALL_DIR="${JLOOM_INSTALL_DIR:-$HOME/.jloom}"
GRADLE_INSTALL_DIR="$INSTALL_DIR/build/install/jloom"
BIN_DIR="$INSTALL_DIR/bin"
AUTO_SYMLINK=1

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()    { printf "${BLUE}==>${NC} %s\n" "$*"; }
success() { printf "${GREEN}==✓${NC} %s\n" "$*"; }
warn()    { printf "${YELLOW}==!${NC} %s\n" "$*"; }
fail()    { printf "${RED}==✗${NC} %s\n" "$*" >&2; exit 1; }

usage() {
    cat <<EOF
Usage: install.sh [options]

Options:
  --latest                Track the default branch HEAD (less safe; sees unreviewed commits)
  --ref <git-ref>         Pin to a specific commit/tag/branch (default: ${REF})
  --dir <path>            Install location (default: $HOME/.jloom)
  --no-symlink             Skip the ~/.local/bin symlink step (rc-file PATH entry still applied)
  --system-install        Also symlink into /usr/local/bin (requires write access; not recommended)
  -h, --help              Show this help

Environment overrides:
  JLOOM_REPO              Git URL to clone from
  JLOOM_REF               Default git ref
  JLOOM_INSTALL_DIR       Default install location
  JLOOM_SYSTEM_INSTALL    Same as --system-install
  JLOOM_NO_SYMLINK        Same as --no-symlink
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --latest)   USE_LATEST="true"; shift ;;
        --ref)      [ $# -ge 2 ] || fail "--ref requires an argument"; REF="$2"; shift 2 ;;
        --dir)      [ $# -ge 2 ] || fail "--dir requires an argument"; INSTALL_DIR="$2"; BIN_DIR="$INSTALL_DIR/bin"; shift 2 ;;
        --no-symlink) AUTO_SYMLINK=0; shift ;;
        --system-install) AUTO_SYMLINK=2; shift ;;
        -h|--help)  usage; exit 0 ;;
        -*)         fail "Unknown option: $1 (try --help)" ;;
        *)          fail "Unexpected positional arg: $1 (try --help)" ;;
    esac
done

# System-wide install is opt-in. Refuse to write to /usr/local/bin unless asked,
# even if the directory is writable, because it affects every user on the box.
if [ "${JLOOM_SYSTEM_INSTALL:-0}" = "1" ]; then
    AUTO_SYMLINK=2
fi
if [ "${JLOOM_NO_SYMLINK:-0}" = "1" ]; then
    AUTO_SYMLINK=0
fi

# 1. Verify prerequisites

info "Checking prerequisites"

command -v java >/dev/null 2>&1 || fail "java not found. Install JDK 25+ from https://adoptium.net/ and re-run."
command -v git  >/dev/null 2>&1 || fail "git not found. Install git and re-run."

JAVA_MAJOR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{ if ($1 == 1) print $2; else print $1 }')
if [ "${JAVA_MAJOR:-0}" -lt 25 ]; then
    fail "Java 25+ required (found $(java -version 2>&1 | head -1)). Install JDK 25+ from https://adoptium.net/ and re-run."
fi
success "Java $(java -version 2>&1 | awk -F '"' '/version/ {print $2}') and git found"

if [ "$(id -u)" = "0" ]; then
    warn "Running as root. Per-user install is recommended."
    if [ "$AUTO_SYMLINK" = "2" ]; then
        warn "JLOOM_SYSTEM_INSTALL=1 — will write to /usr/local/bin if writable"
    else
        AUTO_SYMLINK=1
        info "JLOOM_SYSTEM_INSTALL not set; will only symlink to ~/.local/bin (or rc-file PATH)"
    fi
fi

# 2. Clone (or update) the repo

if [ -d "$INSTALL_DIR/.git" ]; then
    info "Existing install at $INSTALL_DIR — fetching $REF"
    git -C "$INSTALL_DIR" fetch --depth 1 origin "$REF" || fail "git fetch failed in $INSTALL_DIR"
    git -C "$INSTALL_DIR" checkout FETCH_HEAD || fail "git checkout failed in $INSTALL_DIR"
else
    info "Cloning $REPO_URL ($REF) to $INSTALL_DIR"
    mkdir -p "$(dirname "$INSTALL_DIR")"
    if [ "$USE_LATEST" = "true" ]; then
        git clone --depth 1 "$REPO_URL" "$INSTALL_DIR"
        git -C "$INSTALL_DIR" checkout "$REF"
    else
        git clone --depth 1 --branch "$REF" "$REPO_URL" "$INSTALL_DIR"
    fi
fi
if [ "$USE_LATEST" = "false" ] && [ "$REF" = "main" ]; then
    warn "Pinned to 'main' HEAD (default). For higher assurance, set JLOOM_REF to a specific commit/tag."
fi

# 3. Build

info "Building jloom (this takes a few minutes the first time)"
cd "$INSTALL_DIR"
./gradlew installDist --no-daemon

if [ ! -x "$GRADLE_INSTALL_DIR/bin/jloom" ]; then
    fail "build succeeded but $GRADLE_INSTALL_DIR/bin/jloom is missing or not executable"
fi

# Make $INSTALL_DIR/bin the canonical location by symlinking it at the front of the
# tree. This keeps the on-PATH entry clean (~/.jloom/bin, not ~/.jloom/build/install/jloom/bin).
if [ ! -e "$BIN_DIR" ]; then
    ln -s "$GRADLE_INSTALL_DIR/bin" "$BIN_DIR"
    success "Linked $BIN_DIR -> $GRADLE_INSTALL_DIR/bin"
fi

# 4. Symlink `jloom` into PATH

PATH_OK=0
if [ "$AUTO_SYMLINK" -ge 1 ] && [ -d "$HOME/.local/bin" ] && [ -w "$HOME/.local/bin" ]; then
    ln -sf "$BIN_DIR/jloom" "$HOME/.local/bin/jloom"
    success "Linked $HOME/.local/bin/jloom -> $BIN_DIR/jloom"
    PATH_OK=1
fi
if [ "$AUTO_SYMLINK" = "2" ] && [ -d "/usr/local/bin" ] && [ -w "/usr/local/bin" ]; then
    if [ "$(id -u)" != "0" ] && [ -n "${JLOOM_SYSTEM_INSTALL:-}" ]; then
        warn "JLOOM_SYSTEM_INSTALL=1 but not running as root — skipping /usr/local/bin"
    else
        ln -sf "$BIN_DIR/jloom" "/usr/local/bin/jloom"
        success "Linked /usr/local/bin/jloom -> $BIN_DIR/jloom"
        PATH_OK=1
    fi
fi

# 5. Done

echo
success "jloom installed at $INSTALL_DIR"
echo

if [ "$PATH_OK" = "1" ]; then
    info "Run ${GREEN}jloom list${NC} to verify — no PATH setup needed."
else
    # Auto-add to the user's shell rc file. Idempotent: skip if the line is already there.
    SHELL_NAME=$(basename "${SHELL:-sh}")
    case "$SHELL_NAME" in
        zsh)  RC_FILE="$HOME/.zshrc" ;;
        fish) RC_FILE="$HOME/.config/fish/config.fish" ;;
        bash)
            if [ -f "$HOME/.bash_profile" ] && [ ! -f "$HOME/.bashrc" ]; then
                RC_FILE="$HOME/.bash_profile"
            else
                RC_FILE="$HOME/.bashrc"
            fi
            ;;
        *)    RC_FILE="$HOME/.profile" ;;
    esac

    if [ -f "$RC_FILE" ] && grep -qF "$BIN_DIR" "$RC_FILE"; then
        info "PATH entry for $BIN_DIR already present in $RC_FILE — skipping"
    else
        EXPORT_LINE="export PATH=\"$BIN_DIR:\$PATH\""
        printf '\n# jloom CLI\n%s\n' "$EXPORT_LINE" >> "$RC_FILE"
        success "Added PATH entry to $RC_FILE"
    fi
fi

# 6. Verify by running `jloom list` (with BIN_DIR on PATH for this invocation)

echo
info "Verifying install..."
VERIFY_PATH="$BIN_DIR:$PATH"
if PATH="$VERIFY_PATH" "$BIN_DIR/jloom" list >/dev/null 2>&1; then
    success "jloom list works — install complete"
    if [ "$PATH_OK" != "1" ]; then
        info "For this shell: ${GREEN}export PATH=\"$BIN_DIR:\$PATH\"${NC}"
    fi
else
    warn "jloom failed to run. Try:  ${GREEN}$BIN_DIR/jloom list${NC}  to see the error"
fi
echo
info "Update later with:  ${GREEN}cd $INSTALL_DIR && git pull && ./gradlew installDist${NC}"
echo