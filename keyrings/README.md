These keyrings are installed in /usr/share/keyrings/extra-repositories, and used
by the gbp-buildpackage jobs.

To generate an asc file that sbuild understands from an upstream keyring:

  rm -r temp.kbx
  gpg --no-default-keyring --keyring temp.kbx --import < upstream_keyring.asc
  gpg --no-default-keyring --keyring temp.kbx --export > new_keyring.asc

