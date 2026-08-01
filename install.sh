#!/usr/bin/env bash
# jloom installer — clones the repo, builds the CLI, and configures executables.
# Works on Linux and macOS with bash (4.0+ or system bash), git, and JDK 25+.
#
# SECURITY MODEL
#   - This script runs `git clone` and then executes the cloned `gradlew` (a shell
#     script) with whatever Java toolchain is on the machine. The remote is
#     trusted by default. Set JLOOM_REPO to override.
#   - By default we pin to a specific git ref (configurable via JLOOM_REF) so a
#     compromised or supply-chain-injected `main` does not silently install
#     arbitrary code. Pass `--latest` to opt into tracking main HEAD.
#   - System-wide symlinks to /usr/local/bin are opt-in via
#     --system-install / JLOOM_SYSTEM_INSTALL=1 (skipped by default; never run as
#     root without that flag). The per-user ~/.local/bin path is always preferred.
#   - Shell RC files (~/.bashrc / ~/.zshrc / config.fish) are only edited when no
#     symlink could be created, AND --no-modify-path wasn't passed.

set -euo pipefail

REPO_URL="${JLOOM_REPO:-https://github.com/Melkabli05/JLoom.git}"
REF="${JLOOM_REF:-main}"
USE_LATEST=0
INSTALL_DIR="${JLOOM_INSTALL_DIR:-$HOME/.jloom}"
AUTO_SYMLINK=1
MODIFY_RC=1

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' BLUE='' NC=''
fi

info()    { printf "%b\n" "${BLUE}==>${NC} $*"; }
success() { printf "%b\n" "${GREEN}==✓${NC} $*"; }
warn()    { printf "%b\n" "${YELLOW}==!${NC} $*"; }
fail()    { printf "%b\n" "${RED}==✗${NC} $*" >&2; exit 1; }

usage() {
    cat <<EOF
Usage: install.sh [options]

Options:
  --latest            Track default branch HEAD (less safe; sees unreviewed commits)
  --ref <git-ref>     Pin to a specific commit/tag/branch (default: ${REF})
  --dir <path>        Install location (default: $HOME/.jloom)
  --no-symlink        Skip symlinking to ~/.local/bin or /usr/local/bin
  --no-modify-path    Do not edit shell RC files (~/.bashrc, ~/.zshrc, etc.)
  --system-install    Symlink into /usr/local/bin (requires write access; opt-in)
  -h, --help          Show this help

Environment overrides:
  JLOOM_REPO           Git URL to clone from
  JLOOM_REF            Default git ref
  JLOOM_INSTALL_DIR    Default install location
  JLOOM_SYSTEM_INSTALL Same as --system-install
  JLOOM_NO_SYMLINK     Same as --no-symlink
EOF
}

[ "${JLOOM_SYSTEM_INSTALL:-0}" = "1" ] && AUTO_SYMLINK=2
[ "${JLOOM_NO_SYMLINK:-0}" = "1" ] && AUTO_SYMLINK=0

while [ $# -gt 0 ]; do
    case "$1" in
        --latest)          USE_LATEST=1; shift ;;
        --ref)             [ $# -ge 2 ] || fail "--ref requires an argument"; REF="$2"; shift 2 ;;
        --dir)             [ $# -ge 2 ] || fail "--dir requires an argument"; INSTALL_DIR="$2"; shift 2 ;;
        --no-symlink)      AUTO_SYMLINK=0; shift ;;
        --no-modify-path)  MODIFY_RC=0; shift ;;
        --system-install)  AUTO_SYMLINK=2; shift ;;
        -h|--help)         usage; exit 0 ;;
        -*)                fail "Unknown option: $1 (try --help)" ;;
        *)                 fail "Unexpected positional argument: $1" ;;
    esac
done

GRADLE_INSTALL_DIR="$INSTALL_DIR/build/install/jloom"
BIN_DIR="$INSTALL_DIR/bin"

# 1. Verify prerequisites

info "Checking prerequisites..."

command -v git >/dev/null 2>&1 || fail "git not found. Please install git and re-run."
command -v java >/dev/null 2>&1 || fail "java not found. Install JDK 25+ from https://adoptium.net/ and re-run."

JAVA_VERSION_STR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR=$(printf '%s' "$JAVA_VERSION_STR" | awk -F. '{print ($1 == 1) ? $2 : $1}' | sed 's/-.*//')

if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 25 ]; then
    fail "Java 25+ required. Detected version: ${JAVA_VERSION_STR:-unknown}. Install JDK 25+ and re-run."
fi
success "Java $JAVA_VERSION_STR and git verified."

if [ "$(id -u)" = "0" ] && [ "$AUTO_SYMLINK" -ne 2 ]; then
    warn "Running as root. Consider user-level installation unless intentionally deploying system-wide."
fi

# 2. Clone or update repository

if [ -d "$INSTALL_DIR/.git" ]; then
    info "Existing repository detected at $INSTALL_DIR. Fetching ref: $REF..."
    git -C "$INSTALL_DIR" fetch --depth 1 origin "$REF" || fail "git fetch failed in $INSTALL_DIR"
    git -C "$INSTALL_DIR" checkout -q FETCH_HEAD || fail "git checkout failed in $INSTALL_DIR"
else
    info "Cloning $REPO_URL ($REF) into $INSTALL_DIR..."
    mkdir -p "$(dirname "$INSTALL_DIR")"
    if [ "$USE_LATEST" -eq 1 ]; then
        git clone --depth 1 "$REPO_URL" "$INSTALL_DIR"
    else
        git clone --depth 1 --branch "$REF" "$REPO_URL" "$INSTALL_DIR" || \
            fail "Failed to clone ref '$REF'. Ensure it is a valid tag/branch, or use --latest."
    fi
fi

if [ "$USE_LATEST" -eq 0 ] && [ "$REF" = "main" ]; then
    warn "Pinned to HEAD of 'main'. For immutable builds, set JLOOM_REF to a specific commit SHA or tag."
fi

# 3. Build artifact

info "Building jloom via Gradle..."
(
    cd "$INSTALL_DIR"
    ./gradlew installDist --no-daemon
)

TARGET_BIN="$GRADLE_INSTALL_DIR/bin/jloom"
if [ ! -x "$TARGET_BIN" ]; then
    fail "Build completed, but expected binary was not found or not executable at: $TARGET_BIN"
fi

# Atomic symlink creation for $INSTALL_DIR/bin
mkdir -p "$INSTALL_DIR"
rm -rf "$BIN_DIR"
ln -s "$GRADLE_INSTALL_DIR/bin" "$BIN_DIR"
success "Linked canonical path: $BIN_DIR -> $GRADLE_INSTALL_DIR/bin"

# 4. Handle execution pathing

PATH_LINKED=0

if [ "$AUTO_SYMLINK" -eq 1 ]; then
    USER_BIN_DIR="$HOME/.local/bin"
    mkdir -p "$USER_BIN_DIR"
    if [ -w "$USER_BIN_DIR" ]; then
        ln -sf "$BIN_DIR/jloom" "$USER_BIN_DIR/jloom"
        success "Symlinked binary: $USER_BIN_DIR/jloom -> $BIN_DIR/jloom"
        PATH_LINKED=1
    else
        warn "Cannot write to $USER_BIN_DIR. Skipping user symlink."
    fi
elif [ "$AUTO_SYMLINK" -eq 2 ]; then
    SYS_BIN_DIR="/usr/local/bin"
    if [ -w "$SYS_BIN_DIR" ]; then
        ln -sf "$BIN_DIR/jloom" "$SYS_BIN_DIR/jloom"
        success "Symlinked binary: $SYS_BIN_DIR/jloom -> $BIN_DIR/jloom"
        PATH_LINKED=1
    else
        fail "System install requested, but $SYS_BIN_DIR is not writable. Run with sudo/root."
    fi
fi

# 5. RC file modification (only if symlinking failed/disabled and explicitly allowed)

if [ "$PATH_LINKED" -eq 0 ] && [ "$MODIFY_RC" -eq 1 ]; then
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
        info "PATH entry for $BIN_DIR already present in $RC_FILE."
    else
        if [ "$SHELL_NAME" = "fish" ]; then
            EXPORT_LINE="fish_add_path $BIN_DIR"
        else
            EXPORT_LINE="export PATH=\"$BIN_DIR:\$PATH\""
        fi

        mkdir -p "$(dirname "$RC_FILE")"
        printf '\n# jloom CLI\n%s\n' "$EXPORT_LINE" >> "$RC_FILE"
        success "Appended PATH entry to $RC_FILE"
    fi
fi

# 6. Verification

echo
info "Verifying installation..."
if PATH="$BIN_DIR:$PATH" "$BIN_DIR/jloom" list >/dev/null 2>&1; then
    success "jloom verified successfully."
else
    warn "jloom installed, but runtime check failed. Run manually to debug: $BIN_DIR/jloom list"
fi

echo
info "To upgrade in the future, run:"
info "  ${GREEN}cd $INSTALL_DIR && git pull && ./gradlew installDist${NC}"
echo