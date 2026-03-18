module org.ladybug.ladybugpaint {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires java.sql;
    opens org.ladybug.ladybugpaint to javafx.fxml;
    exports org.ladybug.ladybugpaint;
}