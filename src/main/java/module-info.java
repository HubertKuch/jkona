
/**
 * The main module for the Kona application framework.
 * This module declares the dependencies and exports the public packages.
 */
module kona.main {
    requires org.slf4j;
    requires com.google.gson;
    requires org.reflections;

    exports io.github.hubertkuch.kona.application;
    exports io.github.hubertkuch.kona.message;
    exports io.github.hubertkuch.kona.routing;
}
