package conduits

/**
 * User: arjan
 */

class Lazy {

}

//-- Since 0.3.0
//lazyConsume :: (MonadBaseControl IO m, MonadActive m) => Source m a -> m [a]
//lazyConsume (Done _ ()) = return []
//lazyConsume (HaveOutput src _ x) = do
//    xs <- lazyConsume src
//    return $ x : xs
//lazyConsume (PipeM msrc _) = liftBaseOp_ unsafeInterleaveIO $ do
//    a <- monadActive
//    if a
//        then msrc >>= lazyConsume
//        else return []
//lazyConsume (NeedInput _ c) = lazyConsume c
