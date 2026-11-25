{
  description = "Suwayomi-Server: development shell for building and running with the Gradle wrapper on NixOS";

  inputs = {
    # Rolling channel. The exact revision is pinned in flake.lock; bump it with `nix flake update`.
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  # There is deliberately no `packages.default`: a hermetic Nix build would need every Gradle and
  # Maven dependency (Kotlin, JCEF, GraalVM polyglot, the android-jar repo, ...) prefetched into a
  # fixed-output derivation, which is fragile to maintain. Build with `./gradlew` inside the shell.
  outputs =
    { self, nixpkgs }:
    let
      inherit (nixpkgs) lib;

      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});

      # JDK 25: the CI workflows (`actions/setup-java` with `java-version: 25`) and the bundled
      # runtime in scripts/bundler.sh (Zulu 25) both use 25, and CEF is fetched from a JetBrains
      # Runtime 25 release (`webviewJbrRelease` in buildSrc/src/main/kotlin/Constants.kt).
      # The bytecode target stays at Java 21 (`jvmTarget` in gradle/libs.versions.toml), so the
      # jars still run on any JDK >= 21. The full (non-headless) JDK is required because
      # CEFManager calls System.loadLibrary("jawt"), which headless builds do not ship.
      jdkFor = pkgs: pkgs.jdk25;
      minJdk = "21";

      # Shared libraries that native code downloaded or unpacked at run time expects to find in
      # the usual FHS locations, which NixOS does not have:
      #  - KCEF (Chromium Embedded Framework, `server.kcefEnabled`, on by default) is downloaded
      #    from a JetBrains Runtime release into <data dir>/bin/kcef. libcef.so and libjcef.so are
      #    loaded into the JVM with dlopen, so they resolve their dependencies via LD_LIBRARY_PATH.
      #  - The tray icon (`server.systemTrayEnabled`, on by default) uses dorkbox SystemTray,
      #    which loads GTK 3 and libappindicator3 through JNA.
      # Everything else native that the build or server touches (Gradle's native-platform, JNA,
      # webp-imageio, GraalVM's truffle attach library) only needs libc/libstdc++ (see the
      # stdenv.cc.cc.lib entry), and dex2jar/apksig/apk-parser are pure Java.
      runtimeLibsFor =
        pkgs: with pkgs; [
          stdenv.cc.cc.lib # libstdc++, libgcc_s
          zlib
          glib
          nss
          nspr
          dbus
          expat
          cups
          alsa-lib
          systemd # libudev
          libdrm
          libgbm
          libGL
          libxkbcommon
          libx11
          libxcb
          libxcomposite
          libxdamage
          libxext
          libxfixes
          libxrandr
          libxrender
          libxtst
          libxi
          libxshmfence
          atk
          at-spi2-atk
          at-spi2-core
          pango
          cairo
          fontconfig
          freetype
          gtk3
          # dorkbox only probes the libappindicator3 names, not libayatana-appindicator3.
          libappindicator-gtk3
        ];

      # Environment shared by the dev shell and `apps.default`.
      envFor =
        pkgs:
        let
          jdk = jdkFor pkgs;
        in
        assert lib.assertMsg (lib.versionAtLeast jdk.version minJdk)
          "JDK in this nixpkgs (${jdk.version}) is older than the required ${minJdk}";
        {
          JAVA_HOME = jdk.home;
          LD_LIBRARY_PATH = lib.makeLibraryPath (runtimeLibsFor pkgs);
        };

      # git: buildSrc derives the version from `git rev-list HEAD --count`, so it must be on PATH.
      toolsFor = pkgs: [
        (jdkFor pkgs)
        pkgs.git
      ];
    in
    {
      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell (
          envFor pkgs
          // {
            packages = toolsFor pkgs;
          }
        );
      });

      # `nix run` (from the repository root) starts the server in development mode, the same as
      # `nix develop -c ./gradlew :server:run`. Extra arguments are passed on to Gradle.
      apps = forAllSystems (
        pkgs:
        let
          env = envFor pkgs;
          run = pkgs.writeShellApplication {
            name = "suwayomi-server-run";
            runtimeInputs = toolsFor pkgs;
            text = ''
              if [ ! -x ./gradlew ] || [ ! -f ./settings.gradle.kts ]; then
                echo "suwayomi-server-run: run this from the Suwayomi-Server repository root" >&2
                exit 1
              fi
              export JAVA_HOME=${lib.escapeShellArg env.JAVA_HOME}
              export LD_LIBRARY_PATH=${lib.escapeShellArg env.LD_LIBRARY_PATH}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
              exec ./gradlew :server:run "$@"
            '';
          };
        in
        {
          default = {
            type = "app";
            program = lib.getExe run;
            meta.description = "Run Suwayomi-Server in development mode via ./gradlew :server:run";
          };
        }
      );

      # Makes `nix flake check` build the shell, so a broken package set fails the check.
      checks = forAllSystems (pkgs: {
        devShell = self.devShells.${pkgs.stdenv.hostPlatform.system}.default;
      });

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
