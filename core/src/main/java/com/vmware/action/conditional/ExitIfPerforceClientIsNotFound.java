package com.vmware.action.conditional;

import com.vmware.action.BaseAction;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;
import com.vmware.util.StringUtils;

@ActionDescription("Exit if perforce client is not found.")
public class ExitIfPerforceClientIsNotFound extends BaseAction {
    public ExitIfPerforceClientIsNotFound(WorkflowConfig config) {
        super(config);
    }

    @Override
    public void process() {
        String reasonForFailing = perforceClientCannotBeUsed();
        if (StringUtils.isBlank(reasonForFailing) && StringUtils.isBlank(config.perforceClientName)) {
            reasonForFailing = "perforceClientName config value is not set, can also be set by git-p4.client git config value.";
        }
        if (StringUtils.isNotBlank(reasonForFailing)) {
            log.info("");
            log.info("Exiting as " + reasonForFailing);
            System.exit(0);
        }
    }
}
