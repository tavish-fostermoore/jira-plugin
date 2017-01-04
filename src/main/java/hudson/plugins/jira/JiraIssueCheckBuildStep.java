package hudson.plugins.jira;

import com.atlassian.jira.rest.client.api.domain.Status;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.jira.model.JiraIssue;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Created by Reda on 18/12/2014.
 */
public class JiraIssueCheckBuildStep extends Builder implements SimpleBuildStep {

    private String jiraProjectKey;
    private String jiraVersion;

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public JiraIssueCheckBuildStep(String jiraProjectKey, String jiraVersion) {
        this.jiraVersion = jiraVersion;
        this.jiraProjectKey = jiraProjectKey;
    }

    public String getJiraVersion() {
        return jiraVersion;
    }

    public void setJiraVersion(String jiraVersion) {
        this.jiraVersion = jiraVersion;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public void setJiraProjectKey(String jiraProjectKey) {
        this.jiraProjectKey = jiraProjectKey;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        final String realVersion = run.getEnvironment(listener).expand(jiraVersion);
        final String realProjectKey = run.getEnvironment(listener).expand(jiraProjectKey);
        try {
            checkForOpenIssues(run, listener, realVersion, realProjectKey);
        } catch (Exception e) {
            e.printStackTrace(listener.fatalError("Unable to check for open issues for JIRA version %s/%s: %s",
                    realVersion,
                    realProjectKey,
                    e
            ));
            run.setResult(Result.FAILURE);
        }
    }

    private void checkForOpenIssues(final Run<?, ?> run, final TaskListener listener, final String version, final String projectKey) throws IOException, InterruptedException {
        if (isEmpty(version)) {
            throw new IllegalArgumentException("No version specified");
        }
        if (isEmpty(projectKey)) {
            throw new IllegalArgumentException("No project specified");
        }
        listener.getLogger().println(String.format("Checking for open JIRA issues in version '%s' of project %s", jiraVersion, jiraVersion));
        final Set<JiraIssue> issueWithFixVersion = getSiteForProject(run.getParent()).getIssueWithFixVersion(jiraProjectKey, jiraVersion);
        listener.getLogger().println(String.format("Total JIRA issues in version '%s' of any status: '%d'", jiraVersion, issueWithFixVersion.size()));
        final Set<JiraIssue> stillOpenIssues = new HashSet<>();
        for (JiraIssue jiraIssue : issueWithFixVersion) {
            final Status status = jiraIssue.getStatus();
            listener.getLogger().println(String.format("'%s' has status: '%s (%s)'", jiraIssue.getKey(), jiraIssue.getStatus().getName(), jiraIssue.getStatus().getDescription()));
            if (null!= status && status.getName().contains("Open")) {
                stillOpenIssues.add(jiraIssue);
            }
        }
        listener.getLogger().println(String.format("Total open JIRA issues in version '%s': '%d'", jiraVersion, stillOpenIssues.size()));
        if (!stillOpenIssues.isEmpty()) {
            final String issuesToLog = StringUtils.join(stillOpenIssues, ",");
            listener.getLogger().println(String.format("Some JIRA issues in version '%s' are still open. Failing build. The issues are: %s", jiraVersion, issuesToLog));
            run.setResult(Result.FAILURE);
        }
    }

    JiraSite getSiteForProject(Job<?, ?> project) {
        return JiraSite.get(project);
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private DescriptorImpl() {
            super(JiraIssueCheckBuildStep.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            // Placed in the build settings section
            return Messages.JiraIssueCheckBuildStep_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/jira/help.html";
        }

        @Override
        public JiraIssueCheckBuildStep newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            return req.bindJSON(JiraIssueCheckBuildStep.class, formData);
        }
    }
}
