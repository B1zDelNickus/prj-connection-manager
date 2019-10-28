val self = this.project
rootProject.subprojects {
    if (this.name != self.name) {
        dependencies {
            api(self)
        }
    }
}
            