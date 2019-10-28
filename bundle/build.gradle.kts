val self = this.project
dependencies {
    for (p in rootProject.subprojects) {
        if (p.name != self.name && p.name != "service" && !p.name.endsWith("-service")) {
            api(p)
        }
    }
}
            