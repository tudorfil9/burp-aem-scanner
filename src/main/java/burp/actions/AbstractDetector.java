package burp.actions;

import burp.*;
import burp.actions.http.GetRequest;
import burp.actions.http.HttpMethod;
import burp.actions.http.ResponseHolder;
import burp.util.BurpHttpRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Abstract detector which is supposed to scan a set of URL mutations.
 *
 * @author thomas.hartmann@netcentric.biz
 * @since 02/2019
 */
public abstract class AbstractDetector implements SecurityCheck {

    private final IHttpRequestResponse baseMessage;

    private final BurpHelperDto helperDto;

    public AbstractDetector(final BurpHelperDto helperDto, final IHttpRequestResponse baseMessage) {
        this.helperDto = helperDto;
        this.baseMessage = baseMessage;
    }

    @Override
    public Boolean call() throws Exception {
        scan(baseMessage).forEach(iScanIssue -> getHelperDto().getCallbacks().addScanIssue(iScanIssue));
        return true;
    }

    @Override
    public List<IScanIssue> scan(final IHttpRequestResponse baseRequestResponse) {
        final IHttpService httpService = baseRequestResponse.getHttpService();

        final List<IScanIssue> issues = new ArrayList<>();
        if (getExtensions().size() > 0) {
            createPathMutations(getPaths(), getExtensions()).forEach(providePathConsumer(httpService, issues));
        } else {
            getPaths().forEach(providePathConsumer(httpService, issues));
        }

        return issues;
    }

    public Consumer<String> providePathConsumer(final IHttpService httpService, final List<IScanIssue> issues) {
        return path -> {
            try {
                final URL url = new URL(httpService.getProtocol(), httpService.getHost(), httpService.getPort(), path);
                final IHttpRequestResponse requestResponse = this.sendRequest(url, httpService);

                getHelperDto().getCallbacks().printOutput("Request: " + url);
                if (issueDetected(requestResponse)) {
                    report(requestResponse, getName(), String.format(getDescription(), url.toString()), Severity.HIGH, Confidence.CERTAIN)
                            .ifPresent(issue -> issues.add(issue));
                }
            } catch (MalformedURLException e) {
                this.helperDto.getCallbacks().printError("Unable to handle url for path " + path + " " + e);
            }
        };
    }

    /**
     * Sends a request
     *
     * @param url         Url
     * @param httpService http service
     * @return IHttpRequestResponse
     */
    public IHttpRequestResponse sendRequest(final URL url, final IHttpService httpService){
        HttpMethod getMethod = GetRequest.createInstance(this.helperDto, getBaseMessage());
        getMethod.init(url);
        final ResponseHolder responseHolder = getMethod.send();
        return responseHolder.getResponseMessage();
    }

    public Optional<ScanIssue> report(IHttpRequestResponse requestResponse, final String name, final String description,
            final Severity severity, final Confidence confidence) {
        final ScanIssue.ScanIssueBuilder builder = createIssueBuilder(requestResponse, name, description);

        // start here and may add additional information depending on the statuscode.
        builder.withSeverity(severity);

        // success related status codes ... we need to look closely
        builder.withConfidence(confidence);

        return Optional.of(builder.build());
    }

    public List<String> createPathMutations(final String[] paths, final String[] extensions) {
        return createPathMutations(Arrays.asList(paths), Arrays.asList(extensions));
    }

    public List<String> createPathMutations(final List<String> paths, final List<String> extensions) {
        return paths.stream()
                .map(path -> extensions
                        .stream()
                        .map(extension -> formatPath(path, extension))
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public String formatPath(final String path, final String extension){
        return String.format(PATH_PATTERN, path, extension);
    }

    @Override
    public IExtensionHelpers getHelpers() {
        return this.helperDto.getHelpers();
    }

    @Override
    public BurpHelperDto getHelperDto() {
        return this.helperDto;
    }

    public IHttpRequestResponse getBaseMessage() {
        return baseMessage;
    }

    protected abstract boolean issueDetected(final IHttpRequestResponse requestResponse);

    protected abstract List<String> getPaths();

    protected abstract List<String> getExtensions();

}
