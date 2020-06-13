package com.github.lorentz83.alps.communication;

/**
 * Defines the callbacks handled by the Sender class.
 * <p>
 * This is useful to monitor the status of the image transfer.
 */
public interface SenderCallbacks {

    /**
     * Called to update on the progress of the image sent.
     * <p>
     * Note: there are no guarantees on how frequently this function will be called.
     *
     * @param percentage between 0 and 100.
     */
    void progress(int percentage);

    /**
     * Called when sending the image is completed.
     * <p>
     * Note: it may be called multiple times in case of communication error.
     */
    void done();

    /**
     * Called when the image is started sending.
     */
    void start();

    /**
     * Called when an error happen.
     * @param e the thrown exception.
     */
    void onError(Exception e);
}
