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
            buildInputs = with pkgs; [
              openjdk21
              sbt
              git
            ];

            shellHook = ''
              export JAVA_HOME=${pkgs.openjdk21}
              export PATH=${pkgs.sbt}/bin:$PATH
            '';
          };
        };
      };
    };
}