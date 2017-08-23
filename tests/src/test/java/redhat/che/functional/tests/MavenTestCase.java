package redhat.che.functional.tests;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.*;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import redhat.che.functional.tests.fragments.CommandsManagerDialog;
import redhat.che.functional.tests.fragments.CommandsTab;
import redhat.che.functional.tests.fragments.EditCommandsForm;
import redhat.che.functional.tests.fragments.ToolbarDebugPanel;
import redhat.che.functional.tests.fragments.popup.CommandToolbar;
import redhat.che.functional.tests.fragments.popup.DropDownMenu;

import java.util.concurrent.TimeUnit;

import static org.jboss.arquillian.graphene.Graphene.guardAjax;
import static org.jboss.arquillian.graphene.Graphene.waitGui;
import static org.jboss.arquillian.graphene.Graphene.waitModel;

/**
 * Created by katka on 22/06/17.
 */

@RunWith(Arquillian.class)
public class MavenTestCase extends AbstractCheFunctionalTest{
    @FindBy(id="gwt-debug-partButton-Commands")
    private WebElement commandsTab;

    @FindBy(id="gwt-debug-commands-explorer")
    private CommandsTab commandsExplorer;

    @FindBy(id="gwt-debug-editorPartStack-contentPanel")
    private EditCommandsForm editCommandsForm;

    @FindByJQuery("pre:contains('BUILD SUCCESS')")
    private WebElement buildSuccess;

    @FindByJQuery("div#commandsManagerView")
    private CommandsManagerDialog commandsManagerDialog;

    @FindBy(id = "gwt-debug-commandToolbar")
    private CommandToolbar commandToolbar;

    @FindByJQuery("pre:contains('Total time')")
    private WebElement end;


    @FindBy(id="command_" + testName)
    private WebElement newCommand;

    @FindBy(id = "commands_tree-button-remove")
    private WebElement removeButton;

    @FindBy(id = "ask-dialog-ok")
    private WebElement okDialog;

    private final String testName = "buildTest";
    private final String command = "cd ${current.project.path} && scl enable rh-maven33 'mvn clean install'";

    @Before
    public void setup(){
        openBrowser();
        project.select();
    }

    //@After
    public void deleteCommand(){
        newCommand.click();
        removeButton.click();
        okDialog.click();
        waitGui().until().element(newCommand).is().not().visible();
    }

    /**
     * Tries to build project.
     */
    @Test
    @InSequence(1)
    public void test_maven_build() {
        guardAjax(commandsTab).click();
        commandsExplorer.addRunMavenCommand();

        editCommandsForm.fillFieldsAndSave(testName, command);
        commandToolbar.runCommand(testName);
        waitModel().withTimeout(2, TimeUnit.MINUTES).until().element(end).is().visible();
        Assert.assertTrue(buildSuccess.isDisplayed());
    }
}
