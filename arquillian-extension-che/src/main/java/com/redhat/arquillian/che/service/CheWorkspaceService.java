/******************************************************************************* 
 * Copyright (c) 2017 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 *
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 */
package com.redhat.arquillian.che.service;

import java.io.IOException;
import java.util.List;

import com.jayway.jsonpath.JsonPathException;
import com.redhat.arquillian.che.config.CheExtensionConfiguration;
import com.redhat.arquillian.che.resource.Stack;
import com.redhat.arquillian.che.resource.StackService;
import com.redhat.arquillian.che.rest.QueryParam;
import net.minidev.json.JSONArray;
import org.apache.log4j.Logger;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.redhat.arquillian.che.resource.CheWorkspace;
import com.redhat.arquillian.che.resource.CheWorkspaceStatus;
import com.redhat.arquillian.che.rest.RequestType;
import com.redhat.arquillian.che.rest.RestClient;
import com.redhat.arquillian.che.util.OpenShiftHelper;

import okhttp3.Response;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;

public class CheWorkspaceService {

    @Inject
    private static Instance<CheExtensionConfiguration> configurationInstance;

    private static final Logger logger = Logger.getLogger(CheWorkspaceService.class);

    // Interval between querying
    private static long SLEEP_TIME_TICK = 2000;
    // Wait time in seconds
    private static int WAIT_TIME = 300;

    public static Object getDocumentFromResponse(Response response) {
        if (response == null) {
            logger.error(OpenShiftHelper.getCheLogs());
            throw new NullPointerException("Response was null");
        }
        String responseString = null;
        if (response.isSuccessful()) {
            try {
                responseString = response.body().string();
            } catch (IOException e) {
            }
        }
        if (responseString == null) {
            throw new RuntimeException(
                    "Something went wrong and response is empty. The message contains: " + response.message());
        }
        return Configuration.defaultConfiguration().jsonProvider().parse(responseString);
    }

    /**
     * This method parses JSON file obtained when creating workspace. That means that this JSON contains only one workspace (the created one).
     *
     * @param jsonDocument document gotten by creating workspace
     * @return returns workspace set by JSON
     */
    public static CheWorkspace getWorkspaceFromDocument(Object jsonDocument) {
        String workspaceTypePath = "$.config.projects[0].type";
        return new CheWorkspace(getWorkspaceIDELink(jsonDocument), getWorkspaceSelfLink(jsonDocument),
                getWorkspaceRuntimeLink(jsonDocument), getWorkspaceStack(jsonDocument, workspaceTypePath), getWorkspaceName(jsonDocument));
    }

    /**
     * The JSON document of all workspaces is parsed. Workspace with specified name is found (if exists) and returned.
     *
     * @param jsonDocument     JSON document containing all workspaces of user
     * @param cheWorkspaceName name of workspace (same as in user GUI)
     * @return returns null if workspace with specified name does not exists, else returns workspace set by JSON
     */
    public static CheWorkspace getWorkspaceFromDocument(Object jsonDocument, String cheWorkspaceName) {
        String workspaceTypePath = "$[0].config.projects[0].type";
        Object w = JsonPath.read(jsonDocument, "$[?(@.config.name=='" + cheWorkspaceName + "')]");
        if (((JSONArray) w).isEmpty()) {
            return null;
        }
        return new CheWorkspace(getWorkspaceIDELink(w), getWorkspaceSelfLink(w),
                getWorkspaceRuntimeLink(w), getWorkspaceStack(w, workspaceTypePath), cheWorkspaceName);
    }

    private static Stack getWorkspaceStack(Object jsonDocument, String path) {
        String type;
        try{
             type = JsonPath.read(jsonDocument, path);
        }catch (JsonPathException e){
            //no stack type set
            type = "none";
        }
        return StackService.getStackType(type);
    }

    /**
     * Sent a delete request and wait while workspace is existing.
     *
     * @param workspace workspace to delete
     */
    public static void deleteWorkspace(CheWorkspace workspace, String token) {
        logger.info("Deleting " + workspace);
        RestClient client = new RestClient(workspace.getSelfLink());
        client.sendRequest(null, RequestType.DELETE, null, token).close();

        int counter = 0;
        int maxCount = Math.round(WAIT_TIME / (SLEEP_TIME_TICK / 1000));
        logger.info("Waiting for " + WAIT_TIME + " seconds until workspace is deleted from Che server.");
        while (counter < maxCount && workspaceExists(client, workspace)) {
            counter++;
            try {
                Thread.sleep(SLEEP_TIME_TICK);
            } catch (InterruptedException e) {
            }
        }

        if (counter == maxCount && workspaceExists(client, workspace)) {
            logger.error("Workspace has not been deleted on a server after waiting for " + WAIT_TIME + " seconds");
            throw new RuntimeException(
                    "After waiting for " + WAIT_TIME + " seconds the workspace is still existing");
        } else {
            logger.info("Workspace has been successfully deleted from Che server");
            workspace.setDeleted(true);
        }
        client.close();
    }

    public static boolean workspaceExists(RestClient client, CheWorkspace workspace) {
        Response response = client.sendRequest(workspace.getSelfLink(), RequestType.GET);
        boolean isSuccessful = response.isSuccessful();
        response.close();
        return isSuccessful;
    }

    /**
     * Stops a workspace and wait until it is stopped.
     *
     * @param workspace workspace to stop
     */
    public static boolean stopWorkspace(CheWorkspace workspace, String authorizationToken) {
        operateWorkspaceState(workspace, RequestType.DELETE, CheWorkspaceStatus.STOPPED.getStatus(), authorizationToken);
        if (CheWorkspaceService.getWorkspaceStatus(workspace, authorizationToken).equals(CheWorkspaceStatus.STOPPED.getStatus())) {
            return true;
        }
        return false;
    }

    public static boolean startWorkspace(CheWorkspace workspace) {
        CheExtensionConfiguration config = configurationInstance.get();
        RestClient client = new RestClient(config.getCheStarterUrl());
        String path = "/workspace/" + workspace.getName();
        Response response = client.sendRequest(path, RequestType.PATCH, null, config.getKeycloakToken(),
                new QueryParam("masterUrl",
                        config.getCustomCheServerFullURL().isEmpty()
                        ? config.getOpenshiftMasterUrl()
                        : config.getCustomCheServerFullURL()),
                new QueryParam("namespace", config.getOpenshiftNamespace())
        );
        Object jsonDocument = CheWorkspaceService.getDocumentFromResponse(response);
        response.close();
        client.close();

        if(CheWorkspaceService.waitUntilWorkspaceGetsToState(workspace, CheWorkspaceStatus.RUNNING.getStatus(), config.getKeycloakToken()))
            return true;

        return false;
    }

    /**
     * Gets current status of a workspace.
     *
     * @param workspace workspace to get its status
     * @return status of workspace
     */
    public static String getWorkspaceStatus(CheWorkspace workspace, String authorizationToken) {
        logger.info("Getting status of " + workspace);
        RestClient client = new RestClient(workspace.getSelfLink());
        String status = getWorkspaceStatus(client, workspace, authorizationToken);
        client.close();
        return status;

    }

    private static String getWorkspaceStatus(RestClient client, CheWorkspace workspace, String authorizationToken) {
        Response response = client.sendRequest(null, RequestType.GET, null, authorizationToken);
        Object document = getDocumentFromResponse(response);
        response.close();
        return getWorkspaceStatus(document);
    }

    //now used only for deleting workspace
    private static void operateWorkspaceState(CheWorkspace workspace, RequestType requestType, String resultState,
                                              String authorizationToken) {
        RestClient client = new RestClient(workspace.getRuntimeLink());
        client.sendRequest(null, requestType, null, authorizationToken).close();
        client.close();
        try {
            waitUntilWorkspaceGetsToState(workspace, resultState, authorizationToken);
        } catch (Throwable throwable) {
            logger.error(OpenShiftHelper.getCheLogs());
            throw throwable;
        }
    }

    public static boolean waitUntilWorkspaceGetsToState(CheWorkspace workspace, String resultState,
                                                     String authorizationToken) {
        RestClient client = new RestClient(workspace.getSelfLink());
        int counter = 0;
        int maxCount = Math.round(WAIT_TIME / (SLEEP_TIME_TICK / 1000));
        String currentState = getWorkspaceStatus(client, workspace, authorizationToken);
        logger.info("Waiting for " + WAIT_TIME + " seconds until workspace " + workspace.getName() + " gets from state " + currentState
                + " to state " + resultState);
        while (counter < maxCount && !resultState.equals(currentState)) {
            counter++;
            try {
                Thread.sleep(SLEEP_TIME_TICK);
            } catch (InterruptedException e) {
                //TODO: Why is this empty?
            }
            currentState = getWorkspaceStatus(client, workspace, authorizationToken);
            if(currentState.equals(CheWorkspaceStatus.STOPPED.toString()) && resultState.equals(CheWorkspaceStatus.RUNNING.toString())){
                logger.info("Workspace became STOPPED - trying to start it again.");
                return false;
            }
        }

        if (counter == maxCount && !resultState.equals(currentState)) {
			logger.error("Workspace has not successfuly changed its state in required time period of " + WAIT_TIME
					+ " seconds");
			throw new RuntimeException("After waiting for " + WAIT_TIME + " seconds, workspace \""+workspace.getName()+"\" is still"
					+ " not in state " + resultState);
		}
		return true;
	}

    public static String getWorkspaceName(Object jsonDocument) {
        return JsonPath.read(jsonDocument, "$.config.name");
    }

    public static String getWorkspaceRuntimeLink(Object jsonDocument) {
        return getLinkHref(jsonDocument, "start workspace");
    }
    
    public static String getWorkspaceSelfLink(Object jsonDocument) {
        return getLinkHref(jsonDocument, "self link");
    }

    public static String getWorkspaceIDELink(Object jsonDocument) {
        return getLinkHref(jsonDocument, "ide url");
    }

    private static String getWorkspaceStatus(Object jsonDocument) {
        return JsonPath.read(jsonDocument, "$.status");
    }

    private static String getLinkHref(Object workspaceDocument, String rel) {
        String linkPath = "$..links[?(@.rel=='" + rel + "')].href";
        List<String> wsLinks = JsonPath.read(workspaceDocument, linkPath);
        return wsLinks.get(0);
    }


    public static CheWorkspace getRunningWorkspace() {
        CheExtensionConfiguration config = configurationInstance.get();
        String path = "/workspace";
        RestClient client = new RestClient(config.getCheStarterUrl());

        Response response = client.sendRequest(path, RequestType.GET, null, config.getKeycloakToken(),
                new QueryParam("masterUrl",
                        config.getCustomCheServerFullURL().isEmpty()
                        ? config.getOpenshiftMasterUrl()
                        : config.getCustomCheServerFullURL()),
                new QueryParam("namespace", config.getOpenshiftNamespace())
        );
        Object jsonDocument = CheWorkspaceService.getDocumentFromResponse(response);
        CheWorkspace workspace = getRunningWorkspaceFromDocument(jsonDocument);
        return workspace;
    }

    private static CheWorkspace getRunningWorkspaceFromDocument(Object jsonDocument) {
        JSONArray array = JsonPath.read(jsonDocument, "$[?(@.status=='RUNNING')].config.name");
        if (array.size() <= 0) {
            return null;
        } else {
            return getWorkspaceFromDocument(jsonDocument, array.get(0).toString());
        }
    }
}
