

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.foundation.NSRange;
import org.robovm.apple.foundation.NSString;
import org.robovm.apple.gamekit.GKGameCenterControllerDelegateAdapter;
import org.robovm.apple.gamekit.GKGameCenterViewController;
import org.robovm.apple.gamekit.GKGameCenterViewControllerState;
import org.robovm.apple.gamekit.GKLeaderboard;
import org.robovm.apple.gamekit.GKLeaderboardEntry;
import org.robovm.apple.gamekit.GKLeaderboardPlayerScope;
import org.robovm.apple.gamekit.GKLeaderboardScore;
import org.robovm.apple.gamekit.GKLeaderboardTimeScope;
import org.robovm.apple.gamekit.GKLocalPlayer;
import org.robovm.apple.gamekit.GKPlayer;
import org.robovm.apple.gamekit.GKScore;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;
import org.robovm.objc.block.VoidBlock3;
import org.robovm.objc.block.VoidBlock4;

import java.util.ListIterator;

class IGame {
    private GKLeaderboard leaderboard;
    private UIViewController uiViewController;
    private UIViewController postView;
    private IGameListener listener;
    private String leaderBoardId = null;
    private boolean isResumed;
    private boolean isConnecting;
    private boolean scoreLoading;
    public IGame(UIViewController viewController, String leaderBoardId){
        this.uiViewController = viewController;
        this.leaderBoardId = leaderBoardId;
    }

    public void setListener(IGameListener listener) {
        this.listener = listener;
    }
    /**
     * Login if player has not logged in
     */
    public void login(Runnable task){
        if(uiViewController == null)return;
        if(!isActive()){
            if(isConnecting)return;
            isConnecting = true;
            if(postView != null){
                uiViewController.presentViewController(postView, true, null);
            }else {
                GKLocalPlayer player = GKLocalPlayer.getLocalPlayer();
                if(player == null)return;
                player.setAuthenticateHandler(new VoidBlock2<UIViewController, NSError>() {
                    @Override
                    public void invoke(UIViewController viewController, NSError nsError) {
                        isConnecting = false;
                        if (nsError == null) {
                            if (viewController != null) {
                                uiViewController.presentViewController(viewController, true, null);
                            } else if (player.isAuthenticated()) {
                                if (leaderboard == null) {
                                    getLeaderBoard();
                                }
                                listener.onActive();
                                if (task != null) task.run();
                            } else {
                                listener.onError("Please use Game Center settings to log in.");
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * Resume login if already logged in
     */
    public void resume(){
        if(!isActive()){
            GKLocalPlayer player = GKLocalPlayer.getLocalPlayer();
            if (player != null) {
                player.setAuthenticateHandler(new VoidBlock2<UIViewController, NSError>() {
                    @Override
                    public void invoke(UIViewController viewController, NSError nsError) {
                        isConnecting = false;
                        if (nsError == null) {
                            if (player.isAuthenticated()) {
                                if (leaderboard == null) {
                                    getLeaderBoard();
                                }
                                if (!isResumed) {
                                    isResumed = true;
                                    listener.onActive();
                                }
                            } else if (viewController != null) {
                                postView = viewController;
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * Get the leaderBoard associated with game
     */
    private void getLeaderBoard(){
        if(GKLocalPlayer.getLocalPlayer().isAuthenticated()){
            NSArray<NSString> ids = new NSArray<>(new NSString(leaderBoardId));
            GKLeaderboard.loadLeaderboards(ids, new VoidBlock2<NSArray<GKLeaderboard>, NSError>() {
                @Override
                public void invoke(NSArray<GKLeaderboard> gkLeaderboards, NSError nsError) {
                    if(nsError == null){
                        for(GKLeaderboard board : gkLeaderboards){
                            if(board.getBaseLeaderboardID().matches(leaderBoardId)){
                                IGame.this.leaderboard = board;
                                loadScore(board);
                                break;
                            }
                        }
                    }
                }
            });
        }

    }

    /**
     * Submit score to leaderBoard
     * @param score score to be submitted
     * @param task callback
     */
    public void submitScore(int score, ScoreListener task){
        if(isActive()){
            if(leaderboard != null) {
                int s = score + 1;
                GKLocalPlayer player = GKLocalPlayer.getLocalPlayer();
                NSArray<NSString> id = new NSArray<>(new NSString(leaderBoardId));
                GKLeaderboard.submitScore(s, 0, player, id, new VoidBlock1<NSError>() {
                    @Override
                    public void invoke(NSError nsError) {
                        if (nsError == null) {
                            if (task != null) task.onScoreReceived(0, 0);
                        }
                    }
                });
            }
        }

    }

    /**
     * Display leaderBoard UI
     */
    public void showLeaderBoard(){
        if(isActive()) {
            GKGameCenterViewController viewController =
                    new GKGameCenterViewController(leaderBoardId,
                            GKLeaderboardPlayerScope.Global, GKLeaderboardTimeScope.AllTime);
            if (!viewController.equals(null)) {
                viewController.setGameCenterDelegate(new GKGameCenterControllerDelegateAdapter(){
                    @Override
                    public void didFinish(GKGameCenterViewController gameCenterViewController) {
                          gameCenterViewController.dismissViewController(true, null);
                    }
                });
                if(uiViewController != null){
                    uiViewController.presentViewController(viewController, true, null);
                }

            }else {
                if(listener != null)listener.onError("Leaderboard couldn't be displayed " +
                        "on your device.");
            }
        }else {
            if(listener != null)listener.onError("Please login first.");
        }
    }

    /**
     * Load player score from leaderBoard
     * @param task callback
     */
    public void loadScore(ScoreListener task){
        if(!isActive())return;
        if(leaderboard != null) {
            if (!scoreLoading) {
                scoreLoading = true;
                leaderboard.loadEntriesForPlayerScope(GKLeaderboardPlayerScope.Global,
                        GKLeaderboardTimeScope.AllTime, new NSRange(1, 10), new VoidBlock4<GKLeaderboardEntry, NSArray<GKLeaderboardEntry>, Long, NSError>() {
                            @Override
                            public void invoke(GKLeaderboardEntry gkLeaderboardEntry, NSArray<GKLeaderboardEntry> gkLeaderboardEntries, Long aLong, NSError nsError) {
                                scoreLoading = false;
                                if (nsError == null && gkLeaderboardEntry != null) {
                                    int score = (int) gkLeaderboardEntry.getScore();
                                    int rank = (int) gkLeaderboardEntry.getRank();
                                    if (task != null) task.onScoreReceived(score, rank);
                                }
                            }
                        });
            }
        }
    }

    /**
     * Load player score from leaderBoard
     * @param board the leaderBoard to load score from
     */
    private void loadScore(GKLeaderboard board){
        if(!isActive())return;
        if (board != null) {
        if(!scoreLoading) {
            scoreLoading = true;
                board.loadEntriesForPlayerScope(GKLeaderboardPlayerScope.Global,
                        GKLeaderboardTimeScope.AllTime, new NSRange(1, 10), new VoidBlock4<GKLeaderboardEntry, NSArray<GKLeaderboardEntry>, Long, NSError>() {
                            @Override
                            public void invoke(GKLeaderboardEntry gkLeaderboardEntry, NSArray<GKLeaderboardEntry> gkLeaderboardEntries, Long aLong, NSError nsError) {
                                scoreLoading = false;
                                if (nsError == null && gkLeaderboardEntry != null) {
                                    int score = (int) gkLeaderboardEntry.getScore();
                                    int rank = (int) gkLeaderboardEntry.getRank();
                                    listener.onScoreLoaded(score, rank);
                                }
                            }
                        });
            }
        }
    }

    /**
     * Check if player has logged in
     * @return true or false
     */
    public boolean isActive(){
        return GKLocalPlayer.getLocalPlayer().isAuthenticated();
    }
   public void signOut(){
        listener.onError("Please use Game Center settings to log out");
   }

    public interface IGameListener{
        void onActive();
        void onScoreLoaded(int score, int rank);
        void onError(String error);
    }


}
