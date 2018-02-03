package im.djm.blockchain;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import im.djm.blockchain.block.Block;
import im.djm.blockchain.block.Miner;
import im.djm.blockchain.block.data.Data;
import im.djm.blockchain.block.data.Validator;
import im.djm.blockchain.block.nulls.NullBlock;
import im.djm.blockchain.block.nulls.NullTxData;
import im.djm.blockchain.hash.BlockHash;
import im.djm.blockchain.hash.TxHash;
import im.djm.tx.Output;
import im.djm.tx.Tx;
import im.djm.tx.TxData;
import im.djm.tx.TxPool;
import im.djm.tx.Utxo;
import im.djm.tx.UtxoPool;
import im.djm.wallet.WalletAddress;
import im.djm.zMain.SoutUtil;

/**
 * 
 * @author djm.im
 *
 */
public class BlockChain {

	private Map<BlockHash, BlockWrapper> blocks = new HashMap<>();

	private BlockWrapper topBlockWrapper;

	private static Validator<Data> dataValidator = new Validator<Data>() {
	};

	private static List<Predicate<Data>> dataValidationRules = new ArrayList<>();
	static {
		dataValidationRules.add(data -> data != null);
		dataValidationRules.add(data -> true);
	}

	private static Validator<Block> blockValidator = new Validator<Block>() {
	};

	private List<Predicate<Block>> blockValidationRules = new ArrayList<>();

	private WalletAddress minerAddress;

	private static Validator<BlockHash> hashValidator = new Validator<BlockHash>() {
	};

	private static List<Predicate<BlockHash>> hashValidationRules = new ArrayList<>();
	static {
		hashValidationRules.add(hash -> hash != null);
		hashValidationRules.add(hash -> hash.getBinaryLeadingZeros() >= 16);
	}

	private void initBlockValidationRules() {
		this.blockValidationRules.add(block -> block != null);

		// Validate data
		this.blockValidationRules.add(block -> {
			return BlockChain.dataValidator.isValid(block.getData(), BlockChain.dataValidationRules);
		});

		// check if previous block exist in block chain
		this.blockValidationRules.add(block -> {
			BlockHash prevHashKey = block.getPrevBlockHash();
			BlockWrapper prevBlock = this.blocks.get(prevHashKey);
			if (prevBlock == null) {
				return false;
			}
			return true;
		});

		// check if `block` already exist in map
		this.blockValidationRules.add(block -> {
			BlockHash theBlockKey = block.getBlockHash();
			BlockWrapper theBlock = this.blocks.get(theBlockKey);
			if (theBlock != null) {
				return false;
			}
			return true;
		});

	}

	private static final int REWARD = 100;

	private UtxoPool utxoPool;

	private TxPool txPool;

	/**
	 * 
	 * @throws NoSuchAlgorithmException
	 */
	// TODO
	// constructor should be private
	@Deprecated
	public BlockChain() throws NoSuchAlgorithmException {
		this.initBlockValidationRules();

		this.utxoPool = new UtxoPool();
		this.txPool = new TxPool();

		this.initNullBlock();
		this.initNullTxBlock();
	}

	public BlockChain(WalletAddress walletAddress) throws NoSuchAlgorithmException {
		this.initBlockValidationRules();

		this.minerAddress = walletAddress;

		this.utxoPool = new UtxoPool();
		this.txPool = new TxPool();

		this.initNullBlock();
		this.initNullTxBlock();
	}

	// the null block has to be the same for in all nodes
	private void initNullBlock() throws NoSuchAlgorithmException {
		NullBlock nullBlock = new NullBlock();

		this.wrapAndAddBlock(nullBlock, null);
	}

	private void initNullTxBlock() throws NoSuchAlgorithmException {
		NullTxData nullTxData = new NullTxData();

		Block txBlock = generateNewTxBlock(nullTxData, new ArrayList<>());
		this.add(txBlock);
	}

	// TODO
	// think about relation between Blockchain and Miner's wallet
	public void setMiner(WalletAddress walletAddress) {
		this.minerAddress = walletAddress;
	}

	// End: constructor area
	// **************************************************************************************

	/**
	 * 
	 * @param block
	 * @return
	 * 
	 * 		Add block that already exists.
	 */
	public boolean add(Block block) {
		if (BlockChain.blockValidator.isValid(block, this.blockValidationRules)) {

			BlockHash prevBlockHash = block.getPrevBlockHash();
			BlockWrapper prevBlockWrapper = this.blocks.get(prevBlockHash);
			if (prevBlockWrapper == null) {
				// TODO
				// throw an exception
				return false;
			}

			this.wrapAndAddBlock(block, prevBlockWrapper);

			return true;
		}

		return false;
	}

	private void wrapAndAddBlock(Block block, BlockWrapper prevBlockWrapper) {
		BlockHash mapKey = block.getBlockHash();
		BlockWrapper blockWrapper = new BlockWrapper(block, prevBlockWrapper);
		this.blocks.put(mapKey, blockWrapper);
		this.topBlockWrapper = blockWrapper;
	}

	/**
	 * 
	 * @param data
	 * @throws NoSuchAlgorithmException
	 * 
	 *             Deprecated method if used in the first version of blockchain
	 *             (without tx)
	 */
	@Deprecated
	public void add(Data data) throws NoSuchAlgorithmException {
		if (!BlockChain.dataValidator.isValid(data, BlockChain.dataValidationRules)) {
			// TODO: throw exception
			// Cannot add invalid data
			return;
		}

		Block block = generateNewBlock(data);
		this.add(block);
	}

	@Deprecated
	private Block generateNewBlock(Data data) throws NoSuchAlgorithmException {
		Block prevBlock = this.getTopBlock();
		Block block = Miner.createNewBlock(prevBlock, data, BlockChain.hashValidator, BlockChain.hashValidationRules);

		return block;
	}

	public void add(Tx tx, List<Utxo> spentOutputs) throws NoSuchAlgorithmException {
		TxData txData = new TxData();
		txData.add(tx);

		Block block = generateNewTxBlock(txData, spentOutputs);

		this.add(block);
	}

	private Block generateNewTxBlock(TxData txData, List<Utxo> spentOutputs) throws NoSuchAlgorithmException {
		txData = this.addCoinbaseTx(txData);

		this.updateTxPoolAndUtxoPool(txData, spentOutputs);

		Block prevBlock = this.getTopBlock();

		return Miner.createNewBlock(prevBlock, txData, BlockChain.hashValidator, BlockChain.hashValidationRules);
	}

	private TxData addCoinbaseTx(TxData txData) throws NoSuchAlgorithmException {
		Tx coinbaseTx = new Tx(this.minerAddress, BlockChain.REWARD);
		txData.addCoinbaseTx(coinbaseTx);

		return txData;
	}

	private void updateTxPoolAndUtxoPool(TxData txData, List<Utxo> spentOutputs) {
		for (Tx tx : txData.getTxs()) {
			this.txPool.add(tx);
			for (int index = 0; index < tx.getOutputSize(); index++) {
				Utxo utxo = new Utxo(tx.getTxId(), index);
				this.utxoPool.add(utxo);
			}
		}

		for (Utxo utxo : spentOutputs) {
			this.utxoPool.remove(utxo);
		}

		SoutUtil.printlnParagraph("Updated txPool", "Added " + txData, this.txPool.toString());
	}

	private Block getTopBlock() {
		return this.topBlockWrapper.getBlock();
	}

	public List<Utxo> getUtxoFor(WalletAddress walletAddress) {
		List<Utxo> utxoList = new ArrayList<>();
		// TODO
		// use fileter method
		for (Utxo utxo : this.utxoPool.getAll()) {
			Tx tx = this.txPool.getTx(utxo.getTxId());
			Output output = tx.getOutput(utxo.getOutputIndexd());
			if (walletAddress.equals(output.getWalletAddres())) {
				utxoList.add(new Utxo(utxo));
			}
		}

		return utxoList;
	}

	public long getBalance(WalletAddress walletAddress) {
		long sum = 0;
		List<Utxo> utxos = this.getUtxoFor(walletAddress);
		for (Utxo utxo : utxos) {
			Tx tx = this.txPool.getTx(utxo.getTxId());
			Output output = tx.getOutput(utxo.getOutputIndexd());
			if (walletAddress.equals(output.getWalletAddres())) {
				sum += output.getCoinValue();
			}
		}

		return sum;
	}

	// TODO
	// Group getter methods
	public Tx getTxFromPool(TxHash txId) {
		return this.txPool.getTx(txId);
	}

	public String status() {
		return Long.toString(this.getTopBlock().getLength());
	}

	/**
	 *
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Print blockchain from top to bottom (genesis block).");
		sb.append(System.lineSeparator());

		BlockWrapper currentBlockWrapper = this.topBlockWrapper;
		do {
			sb.append(currentBlockWrapper.getBlock());

			currentBlockWrapper = currentBlockWrapper.getPrevBlockWrapper();
		} while (currentBlockWrapper != null);

		return sb.toString();
	}

}
