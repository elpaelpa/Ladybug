module org.ladybug.ladybugpaint {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.ladybug.ladybugpaint to javafx.fxml;
    exports org.ladybug.ladybugpaint;
}