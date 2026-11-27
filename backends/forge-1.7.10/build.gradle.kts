
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The simulation core is plain Java 8: pulled in as sources, as in the other backends.
sourceSets.main {
    java.srcDir(rootProject.file("../../overflight-core/src/main/java"))
    resources.srcDir(rootProject.file("../../common/resources"))
}

// The shared resources carry the Fabric mixin config, which this backend replaces with its own.
tasks.processResources {
    exclude("overflight.mixins.json")
}
