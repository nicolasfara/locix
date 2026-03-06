import Haste.App
import Haste.App.Concurrent
import qualified Control.Concurrent as CC

type Recipient = (SessionID, CC.Chan String)
type RcptList = CC.MVar [Recipient]

srvHello :: Server RcptList -> Server ()
srvHello remoteRcpts = do
  recipients <- remoteRcpts
  sid <- getSessionID
  liftIO $ do
    rcptChan <- CC.newChan
    CC.modifyMVar recipients $ \cs ->
      return ((sid, rcptChan):cs, ())

srvSend :: Server RcptList -> String -> Server ()
srvSend remoteRcpts message = do
  rcpts <- remoteRcpts
  liftIO $ do
    recipients <- CC.readMVar rcpts
    mapM_ (flip CC.writeChan message) recipients

srvAwait :: Server RcptList -> Server String
srvAwait remoteRcpts = do
  rcpts <- remoteRcpts
  sid <- getSessionID
  liftIO $ do
    recipients <- CC.readMVar rcpts
    case lookup sid recipients of
      Just mv -> CC.readChan mv
      _ -> fail "Unregistered session!"

main :: App Done
main = do
  recipients <- liftServerIO $ CC.newMVar []
  hello <- remote $ srvHello recipients
  awaitMsg <- remote $ srvAwait recipients
  sendMsg <- remote $ srvSend recipients

  runClient $ do
    withElems ["log","message"] $ \[log,msgbox] -> do
      onServer hello

      let recvLoop chatlines = do
            setProp log "value" $ unlines chatlines
            message <- onServer awaitMsg
            recvLoop (message : chatlines)
      fork $ recvLoop []

      msgbox `onEvent` OnKeyPress $ \13 -> do
        msg <- getProp msgbox "value"
        setProp msgbox "value" ""
        onServer (sendMsg <.> msg)
