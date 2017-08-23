package redhat.che.functional.tests.fragments;


import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Created by katka on 14/08/17.
 */
public class EditCommandsForm {
    @FindByJQuery("button[id=\"gwt-debug-command-editor-button-run\" input:first-of-type()")
    private WebElement nameInput;

    @FindByJQuery("div[id=\"orion-editor-gwt-uid-289\"] > div:nth-child(3) > div:nth-child(2) > div > span")
    private WebElement commandInput;

    public void fillFields(String testName, String command) {
        nameInput.sendKeys(testName);
        commandInput.sendKeys(command);
    }
}
