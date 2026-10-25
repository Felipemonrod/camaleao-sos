package br.com.camaleao.sos.domain

/**
 * Se o app veio de fora de uma loja, pelo pacote que o instalou. A regra é por lista de
 * lojas, não de instaladores: cada fabricante tem o seu instalador de APK (o Xiaomi usa
 * `com.miui.packageinstaller`, que uma lista de instaladores do Google deixava passar
 * como se fosse loja, e o diagnóstico escondia o caminho da configuração restrita).
 */
object InstallSource {
    private val STORES =
        setOf(
            "com.android.vending", // Google Play
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.xiaomi.market", // GetApps (Xiaomi)
            "com.huawei.appmarket", // AppGallery
            "com.amazon.venezia", // Amazon Appstore
        )

    /** Sem instalador conhecido (adb, navegador, gerenciador de arquivos) conta como fora da loja. */
    fun sideloaded(installer: String?): Boolean = installer == null || installer !in STORES
}
