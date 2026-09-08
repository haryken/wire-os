DESCRIPTION = "Silly wire daemon for training wakeword and setting performance profile"
LICENSE = "Anki-Inc.-Proprietary"                                                                   
LIC_FILES_CHKSUM = "file://${COREBASE}/../victor/meta-qcom/files/anki-licenses/\                           
Anki-Inc.-Proprietary;md5=4b03b8ffef1b70b13d869dbce43e8f09"

SERVICE_FILES = "wired.service wireos-hotspot-manager.service wireos-hotspot-portal.service"

SRC_URI = "file://wired.service file://wireos-hotspot-manager.service file://wireos-hotspot-portal.service"
S = "${UNPACKDIR}"
#UNPACKDIR = "${S}"

inherit systemd

do_install:append () {
   if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
       install -d ${D}${systemd_unitdir}/system/
       for service in ${SERVICE_FILES}; do
           install -m 0644 ${S}/${service} -D ${D}${systemd_unitdir}/system/${service}
       done
   fi
}

RDEPENDS:${PN} += "victor"
DEPENDS += "victor"
FILES:${PN} += "${systemd_unitdir}/system/"
SYSTEMD_SERVICE:${PN} = "${SERVICE_FILES}"

inherit externalsrc

EXTERNALSRC = "${WORKSPACE}/anki/wired"

do_clean:append () {
    dir = bb.data.expand("${EXTERNALSRC}", d)
    os.system('cd "%s" && rm build/wired && rm build/libvector-gobot.so && rm vector-gobot/build/*' % dir)
}


run_victor() {
  export -n CCACHE_DISABLE
  export CCACHE_DIR="${HOME}/.ccache"
  env \
    -u AR \
    -u AS \
    -u BUILD_AR \
    -u BUILD_AS \
    -u BUILD_CC \
    -u BUILD_CCLD \
    -u BUILD_CFLAGS \
    -u BUILD_CPP \
    -u BUILD_CPPFLAGS \
    -u BUILD_CXX \
    -u BUILD_CXXFLAGS \
    -u BUILD_FC \
    -u CPPFLAGS \
    -u LC_ALL \
    -u LD \
    -u LDFLAGS \
    -u MAKE \
    -u NM \
    -u OBJCOPY \
    -u OBJDUMP \
    -u PATCH_GET \
    -u PKG_CONFIG_DIR \
    -u PKG_CONFIG_DISABLE_UNINSTALLED \
    -u PKG_CONFIG_LIBDIR \
    -u PKG_CONFIG_PATH \
    -u PKG_CONFIG_SYSROOT_DIR \
    -u PSEUDO_DISABLED \
    -u PSEUDO_UNLOAD \
    -u RANLIB \
    -u STRINGS \
    -u STRIP \
    -u TARGET_CFLAGS \
    -u TARGET_CPPFLAGS \
    -u TARGET_CXXFLAGS \
    -u TARGET_LDFLAGS \
    -u TOPLEVEL \
    -u WORKSPACE \
    -u base_bindir \
    -u base_libdir \
    -u base_prefix \
    -u base_sbindir \
    -u bindir \
    -u datadir \
    -u docdir \
    -u exec_prefix \
    -u includedir \
    -u infodir \
    -u libdir \
    -u libexecdir \
    -u localstatedir \
    -u mandir \
    -u nonarch_base_libdir \
    -u nonarch_libdir \
    -u oldincludedir \
    -u prefix \
    -u sbindir \
    -u servicedir \
    -u sharedstatedir \
    -u sysconfdir \
    -u systemd_system_unitdir \
    -u systemd_unitdir \
    -u systemd_user_unitdir \
    -u userfsdatadir \
    -i PATH=/usr/bin:/bin:/usr/sbin:/sbin HOME=$HOME PWD="${WORKSPACE}/anki/wired" \
    "$@"
}

do_compile[pseudo] = "0"
do_compile[network] = "1"

do_compile() {
    cd "${EXTERNALSRC}"
    run_victor make
}

do_install () {
    install -d ${D}/usr/bin
    install -d ${D}/anki/bin
    install -d ${D}/etc/wired
    install -p -m 0755 ${WORKSPACE}/anki/wired/build/wired ${D}/usr/bin/
    install -p -m 0755 ${WORKSPACE}/anki/wired/build/wireos-hotspot-portal ${D}/usr/bin/
    install -p -m 0755 ${WORKSPACE}/anki/wired/scripts/vic-setup-ap ${D}/usr/bin/vic-setup-ap
    install -p -m 0755 ${WORKSPACE}/anki/wired/scripts/wireos-hotspot-manager ${D}/usr/bin/wireos-hotspot-manager
    install -p -m 0755 ${WORKSPACE}/anki/wired/scripts/vic-setup-ap ${D}/anki/bin/vic-setup-ap
    cp -R --no-dereference --preserve=mode,links -v ${WORKSPACE}/anki/wired/webroot ${D}/etc/wired/webroot
}

FILES:${PN} += "usr/bin/wired"
FILES:${PN} += "usr/bin/wireos-hotspot-portal"
FILES:${PN} += "usr/bin/vic-setup-ap"
FILES:${PN} += "usr/bin/wireos-hotspot-manager"
FILES:${PN} += "anki/bin/vic-setup-ap"
FILES:${PN} += "etc/wired/webroot"

FILES:${PN}-dev = ""
do_package_qa[noexec] = "1"

INSANE_SKIP:${PN} = " already-stripped ldflags dev-elf"
EXCLUDE_FROM_SHLIBS = "1"
