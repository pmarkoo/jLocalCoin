package im.djm.tx;

import com.google.common.primitives.Longs;

import im.djm.blockchain.BlockUtil;
import im.djm.blockchain.hash.TxHash;

/**
 * 
 * @author djm.im
 *
 */
// Package-private class
class Input {

	private TxHash prevTxId;

	private long outputIndex;

	public Input(TxHash prevTxId, long outputIndex) {
		// TODO
		// Check is previous tx null - throw exception
		this.prevTxId = new TxHash(prevTxId);
		this.outputIndex = outputIndex;
	}

	/**
	 * 
	 * @return
	 */
	public TxHash getPrevTxId() {
		return this.prevTxId;
	}

	public long getOutputIndex() {
		return this.outputIndex;
	}

	public byte[] getRawData() {
		byte[] rawHash = this.prevTxId.getRawHash();
		byte[] indexByteArray = Longs.toByteArray(this.outputIndex);
		byte[] rawInputBytes = BlockUtil.concatenateArrays(rawHash, indexByteArray);

		return rawInputBytes;
	}

	@Override
	public String toString() {
		return "{ Input: {  TxId: " + this.prevTxId + ", OutputIndex: " + this.outputIndex + " } }";
	}

}
