package hudson.plugins.jira.pipeline;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.jira.JiraSession;
import hudson.plugins.jira.JiraSite;
import hudson.plugins.jira.Messages;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Step that promotes individual issue.
 */
public class WorkflowActionStep extends AbstractStepImpl {

    public final String issueKey;

    public final String sourceStatus;

    public final String workflowAction;

    public final String comment;

    @DataBoundConstructor
    public WorkflowActionStep(@Nonnull String issueKey, @Nonnull String sourceStatus, @Nonnull String workflowAction, String comment) {
        this.issueKey = issueKey;
        this.sourceStatus = sourceStatus;
        this.workflowAction = workflowAction;
        this.comment = comment;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getWorkflowAction() {
        return workflowAction;
    }

    public String getComment() {
        return comment;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(WorkflowActionStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "jiraWorkflowAction";
        }

        @Override
        public String getDisplayName() {
            return Messages.WorkflowActionStep_Descriptor_DisplayName();
        }
    }

    public static class WorkflowActionStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient WorkflowActionStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run run;

        @Override
        protected Void run() {
            JiraSite site = JiraSite.get(run.getParent());
            JiraSession session = site.getSession();
            if (session == null) {
                listener.getLogger().println(Messages.FailedToConnect());
                return null;
            }

            Issue issue = session.getIssueByKey(step.issueKey);

            if (issue == null) {
                listener.getLogger().println(String.format("[JIRA] Issue %s not found", step.issueKey));
                return null;
            }

            Status currentStatus = issue.getStatus();
            boolean matched = StringUtils.equalsIgnoreCase(currentStatus.getName(), step.sourceStatus);

            if (!matched) {
                listener.getLogger().println(String.format("[JIRA] Status \"%s\" of issue %s does not match required status \"%s\"",
                        currentStatus.getName(), step.issueKey, step.sourceStatus));
                return null;
            }


            Integer actionId = session.getActionIdForIssue(step.issueKey, step.workflowAction);

            if (actionId == null) {
                listener.getLogger().println(String.format("[JIRA] Invalid workflow action %s for issue %s",
                        step.workflowAction, step.issueKey));
                return null;
            }

            String newStatus = session.progressWorkflowAction(step.issueKey, actionId);

            listener.getLogger().println(String.format("[JIRA] Issue %s transitioned to \"%s\" due to action \"%s\".",
                    step.issueKey, newStatus, step.workflowAction));

            if (isNotEmpty(step.comment)) {
                session.addComment(step.issueKey, step.comment, site.groupVisibility, site.roleVisibility);
            }
            return null;
        }

    }

}
