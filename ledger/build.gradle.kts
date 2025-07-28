plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":util"))
    implementation(project(":tenant"))
    implementation(project(":attachment"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.yaml:snakeyaml")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    runtimeOnly("org.postgresql:postgresql")
}