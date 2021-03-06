package com.sdu.spark.storage;

import com.sdu.spark.SparkException;
import com.sdu.spark.rpc.RpcEndpointRef;
import com.sdu.spark.rpc.SparkConf;
import com.sdu.spark.storage.BlockManagerMessages.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static com.sdu.spark.utils.RpcUtils.getRpcAskTimeout;

/**
 * {@link BlockManagerMaster}职责:
 *
 *  1: BlockManagerMaster实例化(Driver与Executor会在SparkEnv中构建), 调用链:
 *
 *     SparkEnv.create(isDriver)
 *       |
 *       +---> SparkEnv.registerOrLookupEndpoint()[Driver注册BlockManagerMasterEndpoint(Name=BlockManagerMaster)
 *               |                                 Executor向Driver询问BlockManagerMaster节点]
 *               |
 *               +---> 实例化BlockManagerMaster组件
 *
 * 2: 管理Block数据信息
 *
 * @author hanhan.zhang
 * */
@SuppressWarnings("unchecked")
public class BlockManagerMaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockManagerMaster.class);
    public static final String DRIVER_ENDPOINT_NAME = "BlockManagerMaster";

    /**BlockManagerMasterEndpoint的引用*/
    public RpcEndpointRef driverEndpoint;
    private SparkConf conf;
    private boolean isDriver;

    private long timeout;

    public BlockManagerMaster(RpcEndpointRef driverEndpoint, SparkConf conf, boolean isDriver) {
        this.driverEndpoint = driverEndpoint;
        this.conf = conf;
        this.isDriver = isDriver;

        this.timeout = getRpcAskTimeout(this.conf);
    }

    public void removeExecutor(String execId) {
        tell(new RemoveExecutor(execId));
        LOGGER.info("Removed {} successfully in removeExecutor", execId);
    }

    public void removeExecutorAsync(String execId) {
        driverEndpoint.ask(new RemoveExecutor(execId));
        LOGGER.info("Removal of executor {} requested", execId);
    }

    public BlockManagerId registerBlockManager(BlockManagerId blockManagerId, long maxOnHeapMemSize,
                                               long maxOffHeapMemSize, RpcEndpointRef slaveEndpoint) {
        LOGGER.info("Registering BlockManager {}", blockManagerId);
        try {
            BlockManagerId updatedId = (BlockManagerId) driverEndpoint.askSync(new
                    RegisterBlockManager(blockManagerId, maxOnHeapMemSize, maxOffHeapMemSize, slaveEndpoint));
            LOGGER.info("Registered BlockManager {}", updatedId);
            return updatedId;
        } catch (Exception e) {
            throw new SparkException("register block manager failure", e);
        }

    }

    public boolean updateBlockInfo(BlockManagerId blockManagerId, BlockId blockId, StorageLevel storageLevel,
                                   long memSize, long diskSize) {
        try {
            boolean res = (boolean) driverEndpoint.askSync(new
                    UpdateBlockInfo(blockManagerId, blockId, storageLevel, memSize, diskSize));
            LOGGER.info("Updated info of block {}", blockId);
            return res;
        } catch (Exception e) {
            throw new SparkException(String.format("update blockInfo failure, host = %s, block = %s", blockManagerId.hostPort(), blockId), e);
        }
    }

    public BlockManagerId[] getLocations(BlockId blockId) {
        try {
            return (BlockManagerId[]) driverEndpoint.askSync(new GetLocations(blockId));
        } catch (Exception e) {
            throw new SparkException("fetch block location failure, blockId = " + blockId, e);
        }

    }

    // 行表示分区, 列表示Task运行位置BlockManagerId
    public BlockManagerId[][] getLocations(BlockId[] blockIds) {
        try {
            return  (BlockManagerId[][]) driverEndpoint.askSync(new GetLocationsMultipleBlockIds(blockIds));
        } catch (Exception e) {
            throw new SparkException("fetch block location failure, blockId = " + blockIds, e);
        }
    }

    public boolean contains(BlockId blockId) {
        BlockManagerId[] locations = getLocations(blockId);
        return locations.length != 0;
    }

    public Set<BlockManagerId> getPeers(BlockManagerId blockManagerId) {
        try {
            Set<BlockManagerId> res = (Set<BlockManagerId>) driverEndpoint.askSync(new GetPeers(blockManagerId));
            return res;
        } catch (Exception e) {
            throw new SparkException("fetch block manager peers failure, id = " + blockManagerId, e);
        }
    }

    public RpcEndpointRef getExecutorEndpointRef(String executorId) {
        try {
            return (RpcEndpointRef) driverEndpoint.askSync(new GetExecutorEndpointRef(executorId));
        } catch (Exception e) {
            throw new SparkException("fetch executor point ref failure, id = " + executorId, e);
        }
    }


    public void removeBlock(BlockId blockId) {
        try {
            driverEndpoint.askSync(new RemoveBlock(blockId));
        } catch (Exception e) {
            throw new SparkException("remove block failure, id = " + blockId, e);
        }
    }

    public void removeRdd(int rddId, boolean blocking) {

    }

    public void removeShuffle(int shuffleId, boolean blocking) {

    }

    public void removeBroadcast(long broadcastId, boolean removeFromMaster, boolean blocking) {

    }

    public Map<BlockManagerId, Pair<Long, Long>> getMemoryStatus() {
        try {
            return (Map<BlockManagerId, Pair<Long, Long>>) driverEndpoint.askSync(new GetMemoryStatus());
        } catch (Exception e) {
            throw new SparkException("fetch executor memory status failure", e);
        }
    }

    /**
     * Return the block's status on all block managers, if any. NOTE: This is a
     * potentially expensive operation and should only be used for testing.
     *
     * If askSlaves is true, this invokes the master to query each block manager for the most
     * updated block statuses. This is useful when the master is not informed of the given block
     * by all block managers.
     */
    public Map<BlockManagerId, BlockManagerInfo.BlockStatus> getBlockStatus(BlockId blockId, boolean askSlaves) {
        throw new UnsupportedOperationException("");
    }

    public BlockId[] getMatchingBlockIds(MatchingBlockFilter matchingBlockFilter, boolean askSlaves) {
        throw new UnsupportedOperationException("");
    }


    public boolean hasCachedBlocks(String executorId) {
        try {
            return (boolean) driverEndpoint.askSync(new HasCachedBlocks(executorId));
        } catch (Exception e) {
            throw new SparkException("check has cache block failure, id = " + executorId, e);
        }
    }

    /** Stop the driver endpoint, called only on the Spark driver node */
    public void stop() {
        if (driverEndpoint != null && isDriver) {
            tell(new StopBlockManagerMaster());
            driverEndpoint = null;
            LOGGER.info("BlockManagerMaster stopped");
        }
    }

    private void tell(Object message) {
        if (driverEndpoint != null) {
            try {
                boolean result = (boolean) driverEndpoint.askSync(message);
                if (!result) {
                    throw new SparkException("BlockManagerMasterEndpoint returned false, expected true.");
                }
            } catch (Exception e) {
                throw new SparkException(e.getMessage(), e);
            }
        }
    }
}
