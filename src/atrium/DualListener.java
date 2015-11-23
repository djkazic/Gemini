package atrium;

import java.nio.file.Files;
import java.util.ArrayList;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import data.Data;
import data.DataTypes;
import io.BlockedFile;
import io.StreamedBlock;
import io.StreamedBlockedFile;
import requests.Request;
import requests.RequestTypes;

public class DualListener extends Listener {
	
	private int inOut;
	private String blockOrigin;
	private String blockName;
	
	public DualListener(int inOut) {
		super();
		this.inOut = inOut;
	}

	//New connection, either incoming or outgoing
	public void connected(Connection connection) {
		if(inOut == 1) {
			Utilities.log(this, "New incoming peer");
		} else {
			Utilities.log(this, "New outgoing peer");
		}
		try {
			if(inOut == 1) {
				new Peer(connection, inOut);
			} else {
				new Peer(connection, inOut);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	//New incoming packet post-connection
	public void received(Connection connection, Object object) {
		Peer foundPeer = Peer.findPeer(connection);
		
		if(object instanceof Request) {
			Request request = (Request) object;
			String type = request.getType();

			switch(type) {
				//Requests below are non-encrypted
			
				case RequestTypes.PUBKEY:
					Utilities.log(this, "Received request for pubkey");
					connection.sendTCP(new Data(DataTypes.PUBKEY, Core.pubKey));
					Utilities.log(this, "\tSent pubkey back");
					break;
			
				case RequestTypes.MUTEX:
					Utilities.log(this, "Received request for mutex");
					connection.sendTCP(new Data(DataTypes.MUTEX, Core.rsa.encrypt(Core.mutex, foundPeer.getPubkey())));
					Utilities.log(this, "\tSent mutex back");
					break;
					
				//Requests below are symmetrically encrypted
	
				case RequestTypes.PEERLIST:
					Utilities.log(this, "Received request for peerlist");
					//TODO: more refined peerList filtering
					ArrayList<String> refinedPeerList = new ArrayList<String> ();
					for(Peer peer : NetHandler.peers) {
						//if(peer.externallyVisible())
						String peerData = peer.getConnection().getRemoteAddressTCP().getHostString() + ":"
										+ peer.getConnection().getRemoteAddressTCP().getPort();
						refinedPeerList.add(Core.aes.encrypt(peerData));
					}
					connection.sendTCP(new Data(DataTypes.PEERLIST, refinedPeerList));
					Utilities.log(this, "\tSent peerlist back");
					if(foundPeer.getInOut() == 0) {
						foundPeer.getDeferredLatch().countDown();
					}
					break;
					
				case RequestTypes.SEARCH:
					Utilities.log(this, "Received request for search");
					String encryptedQuery = (String) request.getPayload();
					String decrypted = foundPeer.getAES().decrypt(encryptedQuery);
					//For all known BlockedFiles, check if relevant
					ArrayList<StreamedBlockedFile> streams = new ArrayList<StreamedBlockedFile> ();
					for(BlockedFile bf : Core.blockDex) {
						if(bf.matchSearch(decrypted)) {
							streams.add(bf.toStreamedBlockedFile());
						}
					}
					connection.sendTCP(new Data(DataTypes.SEARCH, streams));
					Utilities.log(this, "\tSent search results back");
					//Search results are ArrayList<StreamedBlockedFile> which have encrypted name + onboard encrypted blockList
					break;
					
				case RequestTypes.BLOCK:
					Utilities.log(this, "Received request for block:");
					String[] encryptedBlock = (String[]) request.getPayload();
					blockOrigin = foundPeer.getAES().decrypt(encryptedBlock[0]);
					blockName = foundPeer.getAES().decrypt(encryptedBlock[1]);
					final Connection blockConn = connection;
					
					(new Thread(new Runnable() {
						public void run() {
							//TODO: search for block
							BlockedFile foundBlock;
							if((foundBlock = FileUtils.getBlockedFile(blockOrigin)) != null) {
								int blockPosition;
								if((blockPosition = foundBlock.getBlockList().indexOf(blockName)) != -1) {
									
									byte[] searchRes = null;
									if(foundBlock.isComplete()) {
										//Attempt complete search
										searchRes = FileUtils.findBlockRAF(foundBlock, blockPosition);
									} else {
										//Attempt incomplete search
										try {
											searchRes = Files.readAllBytes(FileUtils.findBlockAppData(blockOrigin, blockName).toPath());
										} catch (Exception ex) {
											ex.printStackTrace();
										}
									}
									
									if(searchRes != null) {
										Utilities.log(this, "\tSending back block, length: " + searchRes.length);
										blockConn.sendTCP(new Data(DataTypes.BLOCK, new StreamedBlock(blockOrigin, blockName, searchRes)));
									} else {
										Utilities.log(this, "\tFailure: could not find block " + blockName);
									}
								} else {
									Utilities.log(this, "\tFailure: BlockedFile block mismatch; blockList: " 
														+ foundBlock.getBlockList());
								}
							} else {
								Utilities.log(this, "\tFailure: don't have origin BlockedFile");
							}
						}
					})).start();
					break;
			}
		} else if(object instanceof Data) {
			Data data = (Data) object;
			String type = data.getType();

			switch(type) {
				//Data below are encryption keys, mutex is encrypted via RSA
			
				case DataTypes.PUBKEY:
					Utilities.log(this, "Received pubkey data: ");
					String pubkeyData = (String) data.getPayload();
					if(foundPeer.setPubkey(pubkeyData)) {
						foundPeer.getPubkeyLatch().countDown();
					}
					break;
			
				case DataTypes.MUTEX:
					Utilities.log(this, "Received mutex data");
					String encryptedMutex = (String) data.getPayload();
					try {
						String mutexData = Core.rsa.decrypt(encryptedMutex);
						if(foundPeer.mutexCheck(mutexData)) {
							foundPeer.getCryptoLatch().countDown();
						}
					} catch (Exception ex) {
						Utilities.log(this, "Failed to set mutex");
						ex.printStackTrace();
					}
					break;
	
				//All data past this point is encrypted via symmetric encryption
				//TODO: symmetric encryption for peerlist and on
					
				case DataTypes.PEERLIST:
					Utilities.log(this,  "Received peerlist data");
					//TODO: implement peerlist processing
					Object payload = data.getPayload();
					if(payload instanceof ArrayList<?>) {
						ArrayList<String> finishedList = new ArrayList<String> ();
						ArrayList<?> potentialList = (ArrayList<?>) payload;
						for(int i=0; i < potentialList.size(); i++) {
							Object o = potentialList.get(i);
							if(o instanceof String) {
								String encrypted = (String) o;
								String decrypted = foundPeer.getAES().decrypt(encrypted);
								finishedList.add(decrypted);
							}
						}
						Utilities.log(this, "\tPeerlist: " + finishedList);
					}
					break;
					
				case DataTypes.SEARCH:
					Utilities.log(this,  "Received search reply data");
					Object searchPayload = data.getPayload();
					if(searchPayload instanceof ArrayList<?>) {
						ArrayList<?> potentialStreams = (ArrayList<?>) searchPayload;
						for(int i=0; i < potentialStreams.size(); i++) {
							Object o = potentialStreams.get(i);
							if(o instanceof StreamedBlockedFile) {
								StreamedBlockedFile sbl = (StreamedBlockedFile) o;
								BlockedFile intermediate = sbl.toBlockedFile(foundPeer.getAES());
								
								//Store name and blockList in preparation for Download thread fetching from GUI
								String name = intermediate.getPointer().getName();
								ArrayList<String> blockList = intermediate.getBlockList();
								Core.index.put(name, blockList);
								
								String sizeEstimate = "";
								int estimateKb = (int) ((Core.blockSize * blockList.size()) / 1000);
								if(estimateKb > 1000) {
									int estimateMb = (int) (estimateKb / 1000D);
									sizeEstimate += estimateMb + "MB";
								} else {
									sizeEstimate += estimateKb + "KB";
								}
								
								Core.mainWindow.addRowToSearchModel(new String[] {name, sizeEstimate});
							}
						}
					}
					break;
					
				case DataTypes.BLOCK:
					Utilities.log(this, "Received block data");
					StreamedBlock streamedBlock = (StreamedBlock) data.getPayload();
					String origin = foundPeer.getAES().decrypt(streamedBlock.getOrigin());
					Utilities.log(this, "\tBlock origin: " + origin + ", size = " + foundPeer.getAES().decrypt(streamedBlock.getFileBytes()).length);
					streamedBlock.insertSelf(foundPeer.getAES());
					break;
			}
		}
	}
}