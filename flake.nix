# flake.nix
{
  description = "Dev shell for locix";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
  };

  outputs = { self, nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
    in {
      devShells = {
        "${system}" = {
          default = pkgs.mkShell {
            LLVM_BIN = pkgs.clang + "/bin";
            buildInputs = with pkgs; [
                stdenv
                mill
                openjdk21
                boehmgc
                libunwind
                clang
                zlib
            ];

            shellHook = ''
              export JAVA_HOME=${pkgs.openjdk21}
              export PATH=${pkgs.sbt}/bin:${pkgs.clang}/bin:${pkgs.llvm}/bin:$PATH
            '';
          };
        };
      };
    };
}