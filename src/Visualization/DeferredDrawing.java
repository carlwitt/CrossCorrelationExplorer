package Visualization;

/**
 *
 * Created by Carl Witt on 28.10.14.
 */
public interface DeferredDrawing {

    public void drawContents();

    public boolean isDeferringDrawRequests();
    /**
     * If true, calling {@link #drawContents()} will have no effect but setting a dirty flag to true.
     * If false, all draw requests are instantly executed. If the  and the dirty flag is true, a redraw is performed.
     * @param deferDrawRequests Whether to defer invocations of {@link #drawContents()}
     */
    public void setDeferringDrawRequests(boolean deferDrawRequests);

}
