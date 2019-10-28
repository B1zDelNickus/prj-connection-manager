package codes.spectrum.connection_manager


/**
 * Описатель профиля окружения
 */
data class Profile(
    /**
     * Код профиля
     */
    val code: String,
    /**
     * Имя профиля
     */
    val name: String,
    /**
     * Профиль на понижение - суть простая, находясь в Prod разрешено
     * например использовать сервисы Stage автоматически
     * если нет настроек на продуктовые
     */
    val fallBackProfile: Profile? = Default
) {

    companion object {
        val Default = Profile("default", "Условный профиль по умолчанию", null)
        val Local = Profile("local", "Локальное окружение разработчика")
        val CI = Profile("ci", "Окружение в рамках работы CI", Local)
        val Dev = Profile("dev", "Окружение dev-стенда", Local)
        val Stage = Profile("stage", "Окружение stage-стенда", Dev)
        val Prod = Profile("prod", "Окружение prod-стенда", Stage)
        val standardProfiles = arrayOf(Default, Local, CI, Dev, Stage, Prod)

        /**
         * Быстрый маркер для обозначения того, что мы находимся в CI
         */
        const val CI_MARKER = "IS_CI"
        /**
         * Основная настройка, выставляющая имя профиля
         */
        const val SPECTRUM_PROFILE_ENV = "SPECTRUM_PROFILE"
        private const val TRUE_STRING = "true"
        private fun getConfig(name: String, customEnvironment: Map<String, String>?) =
            customEnvironment?.get(name) ?: System.getenv(name) ?: ""

        private fun checkConfig(name: String, customEnvironment: Map<String, String>?) =
            getConfig(name, customEnvironment) == TRUE_STRING

        fun autoDetect(customEnvironment: Map<String, String>? = null): Profile {
            val directProfile = getConfig(SPECTRUM_PROFILE_ENV, customEnvironment)
            if (directProfile.isNotBlank()) {
                return get(directProfile, "Профиль определенный пользователем")
            }
            if (checkConfig(CI_MARKER, customEnvironment)) return CI
            return Local
        }

        fun get(code: String, name: String? = null) = standardProfiles.firstOrNull {
            it.code.toUpperCase() == code.toUpperCase()
        }?.let { if (null == name) it else it.copy(name = name) } ?: Profile(code, name ?: "")

        val Current by lazy { autoDetect() }

    }

}