package redhat.che.functional.tests.fragments;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.jboss.arquillian.graphene.fragment.Root;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;

import static org.jboss.arquillian.graphene.Graphene.*;
/**
 * Created by katka on 02/08/17.

 */
public class CommandsTab {
    @Drone
    private WebDriver driver;

    @FindByJQuery("div[id=\"goal_Run\"] > span[id=\"commands_tree-button-add\"]")
    private WebElement addRunCommand;


    public void addRunMavenCommand(){
        addRunCommand.click();
        waitGui().until().element(addRunCommand).is().not().selected();
        WebElement mvnCommand = driver.findElement(By.xpath("//option[@value=\"mvn\"]"));
        new Actions(driver).doubleClick(mvnCommand).perform();
        waitModel().until().element(mvnCommand).is().not().visible();

//        nameInput = driver.findElement(By.xpath("//button[@id=\"gwt-debug-command-editor-button-run\"]/following-sibling::input"));
//        waitModel().until().element(nameInput).is().visible();
//        nameInput.sendKeys(name);
//
//        cmdInput = driver.findElement(By.xpath("//div[@id=\"orion-editor-gwt-uid-289\"]/div[3]/div[2]/div/span"));
//        cmdInput.sendKeys(cmd);
//        System.out.println("at the end of addRunMavenCommand method");
    }

}
