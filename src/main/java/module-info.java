
module kona.main {
    requires org.slf4j;
    requires com.google.gson;
    requires org.reflections;

    exports io.github.hubertkuch.kona.application;
    exports io.github.hubertkuch.kona.message;
    exports io.github.hubertkuch.kona.routing;
}
