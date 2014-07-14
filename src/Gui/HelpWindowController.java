package Gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * The help window is just an integrated browser to display online help.
 * Created by Carl Witt on 30.06.14.
 */
public class HelpWindowController extends WindowController implements Initializable {

    @FXML WebView browser;
    public String homePage = "https://github.com/carlwitt/CrossCorrelationExplorer/wiki";

    public void goHome(){
        browser.getEngine().load(homePage);
    }

    public void back(){
        try { browser.getEngine().getHistory().go(-1); }
        catch (IndexOutOfBoundsException e) { }
    }

    public void forward(){
        try { browser.getEngine().getHistory().go(1); }
        catch (IndexOutOfBoundsException e) {}
    }

    @Override public void initialize(URL location, ResourceBundle resources) { goHome(); }

}
