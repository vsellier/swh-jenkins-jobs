#!/bin/bash

set -eux

error () {
  echo "Error: $@" >&2
  exit 1
}

debian_mirror=http://deb.debian.org/debian/

chroot_basename="${distribution:=unstable}-${architecture:=amd64}-sbuild"
chroot="source:${chroot_basename}"
chroot_path="/srv/softwareheritage/sbuild/$distribution-$architecture"

if ! schroot -i -c $chroot >/dev/null; then
  echo "Configuring chroot $chroot"
  if [ $distribution = 'sid' ]; then
    aliases=(unstable experimental)
  else
    aliases=("${distribution}-backports")
  fi

  chroot_aliases=("$distribution-swh-$architecture")
  for alias in $aliases; do
    chroot_aliases+=("$alias-$architecture" "$alias-swh-$architecture")
  done

  sbuild_aliases=()
  for alias in "${chroot_aliases[@]}"; do
      sbuild_aliases+=("${alias}-sbuild")
  done

  body="$(cat << EOF
type=directory
directory=$chroot_path
groups=root,sbuild
root-groups=root,sbuild
source-groups=root,sbuild
source-root-groups=root,sbuild
union-type=overlay
union-overlay-directory=/var/run
EOF
          [ $architecture = i386 ] && "echo personality=linux32"
          echo
          )"

  (
    IFS=,
    echo "[$distribution-$architecture]"
    echo "aliases=${chroot_aliases[*]}"
    echo "$body"
    echo
    echo "[$distribution-$architecture-sbuild]"
    echo "profile=sbuild"
    echo "aliases=${sbuild_aliases[*]}"
    echo "$body"
  ) | sudo tee /etc/schroot/chroot.d/$chroot_basename >/dev/null
fi

LOCKDIR="/var/lock/sbuild-package"
if ! test -d $LOCKDIR; then
  mkdir $LOCKDIR
  chgrp sbuild $LOCKDIR
  chmod 3775 $LOCKDIR
fi
umask 002

(
  cd /
  set -x
  flock --exclusive 9

  if ! test -d $chroot_path; then
    echo "Creating chroot in $chroot_path"
    sudo debootstrap --variant=buildd --arch=$architecture --include apt-transport-https,ca-certificates $distribution $chroot_path $debian_mirror
  fi

  schroot -u root -c $chroot -- bash << EOF
    set -ex
    echo deb $debian_mirror $distribution main > /etc/apt/sources.list

    export DEBIAN_FRONTEND=noninteractive
    export UCF_FORCE_CONFFNEW=y UCF_FORCE_CONFFMISS=y

    apt-get update

    apt-get -y -o DPkg::Options::=--force-confnew dist-upgrade
    apt-get -y autoremove --purge
EOF

) 9> $LOCKDIR/$distribution-$architecture.lock
