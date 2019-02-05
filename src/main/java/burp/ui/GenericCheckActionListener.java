package burp.ui;

import burp.BurpHelperDto;
import burp.IHttpRequestResponse;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adds the default get servlet checks to the menu. Instantiates a list of generic callables and triggers them via a threadpool.
 *
 * @author thomas.hartmann@netcentric.biz
 * @since 02/2019
 */
public class GenericCheckActionListener implements ActionListener {

    private final BurpHelperDto helperDto;
    private final Class<Callable>[] callableTypes;

    /**
     * {@link Constructor} for a generic action listener
     *
     * @param helperDto     The DTO for burp internal functionality
     * @param callableTypes {@link Class}s to create
     */
    public GenericCheckActionListener(final BurpHelperDto helperDto, final Class<Callable>... callableTypes) {
        this.helperDto = helperDto;
        this.callableTypes = callableTypes;
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        this.helperDto.getCallbacks().printOutput("GenericCheckActionListener triggered. " + event.toString());
        final ExecutorService pool = Executors.newFixedThreadPool(10);

        final IHttpRequestResponse[] messages = this.helperDto.getiContextMenuInvocation().getSelectedMessages();
        // now we start crafting requests for our vulnerabilities
        for (final IHttpRequestResponse baseMessage : messages) {
            for (final Class callableType : callableTypes) {
                final Optional<Callable> callable = createCallable(baseMessage, callableType);
                callable.ifPresent(c -> pool.submit(c));
            }
            this.helperDto.getCallbacks().printOutput("Misconfiguration related callables submitted for execution");
        }
    }

    private Optional<Callable> createCallable(IHttpRequestResponse baseMessage, Class callableType) {
        Callable callable = null;
        try {
            final Class<?> clazz = Class.forName(callableType.getName());
            this.helperDto.getCallbacks().printOutput("Creating: " + callableType.getName());

            final Constructor<?> constructor = clazz.getConstructor(BurpHelperDto.class, IHttpRequestResponse.class);
            callable = (Callable) constructor.newInstance(new Object[] { this.helperDto, baseMessage });
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            this.helperDto.getCallbacks().printError(String.format("Unable to instantiate %s %s", callableType.getName(), e));
        }

        return Optional.ofNullable(callable);
    }

}
