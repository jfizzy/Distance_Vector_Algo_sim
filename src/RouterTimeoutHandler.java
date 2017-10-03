import java.util.TimerTask;

/**
 *
 * @author jmac
 */
public class RouterTimeoutHandler extends TimerTask{
    
        private Router router;
    
        public RouterTimeoutHandler(Router router) {
            this.router=router;
        }

        @Override
        public void run() {
            router.processTimeout();
        }
}
