package codes.spectrum.connection_manager


class ConnectionDescriptorManager(
    val descriptorConfig: ConnectorDescriptor.Config = ConnectorDescriptor.Config.Default,
    val credenditalsConfig: ConfiguredConnectionCredentials.Config = ConfiguredConnectionCredentials.Config.Default,
    val defaultQuery: ConnectorDescriptor.Query = ConnectorDescriptor.Query.Empty,
    rewriter: ConnectorDescriptor.SetOf.() -> ConnectorDescriptor.SetOf = { this }
) {
    @delegate:Transient
    private val raw_descriptors by lazy { descriptorConfig.build() }
    @delegate:Transient
    private val credentials by lazy { credenditalsConfig.build() }
    @delegate:Transient
    private val descriptors by lazy {
        ConnectorDescriptor.SetOf().apply {
            addAll(raw_descriptors.map {
                credentials.applyTo(it)
            })
        }.rewriter()
    }
    private val queryCache = mutableMapOf<ConnectorDescriptor.Query, Set<ConnectorDescriptor>>()
    fun resolve(query: ConnectorDescriptor.Query = ConnectorDescriptor.Query.Empty): Set<ConnectorDescriptor> =
        queryCache.getOrPut(query) { descriptors.filter { it.matches(defaultQuery) && it.matches(query) }.toSet() }

    companion object {
        val Default = ConnectionDescriptorManager()
    }
}