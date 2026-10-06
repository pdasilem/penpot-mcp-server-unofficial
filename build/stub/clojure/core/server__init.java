package clojure.core;

import clojure.lang.AFn;
import clojure.lang.RT;

public class server__init {
    static {
        RT.var("clojure.core.server", "start-servers", new AFn() {
            @Override
            public Object invoke(Object properties) {
                return null;
            }
        });
    }

    public static void load() {
    }
}
