package com.r3.conclave.sample.client;

import com.r3.conclave.client.EnclaveConstraint;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519PrivateKey;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.PostOffice;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.io.*;
import java.util.UUID;
import java.util.List;
import com.r3.conclave.shaded.kotlin.Pair;
import com.r3.conclave.sample.dataanalysis.common.UserProfile;



public class Client {


    public static void main(String[] args) throws Exception {
        // This is the client that will upload secrets to the enclave for processing.

        if (args.length == 0) {
            System.err.println("Please pass the user data for processing as argument to the command line using --args=\"data fields with space separation \"");
            return;
        }
        ArrayList<UserProfile> l = new ArrayList<UserProfile>() {
        };
        String constraint = args[0] + " " + args[1];
        for (int i = 2; i < args.length; i = i + 4) {
            UserProfile u = new UserProfile(args[i],
                    Integer.parseInt(args[i + 1]),
                    args[i + 2],
                    args[i + 3]);
            l.add(u);

        }

        // Connect to the host, it will send us a remote attestation (EnclaveInstanceInfo).
        // Establish a TCP connection with the host
        Pair<DataInputStream, DataOutputStream> streams = establishConnection();
        DataInputStream fromHost = streams.getFirst();
        DataOutputStream toHost = streams.getSecond();

        //Verify attestation here
        EnclaveInstanceInfo attestation = verifyAttestation(fromHost, constraint);

        // Check it's the enclave we expect. This will throw InvalidEnclaveException if not valid.
        System.out.println("Connected to " + attestation);

        // Serialize and send the data to enclave.
        // Reference for stream of bytes
        byte[] stream = null;
        // ObjectOutputStream is used to convert a Java object into OutputStream
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            for (int i = 0; i < l.size(); i++) {
                oos.writeObject(l.get(i));
            }
            oos.writeObject(null);
            oos.close();
            baos.close();
            stream = baos.toByteArray();


        } catch (IOException e) {
            // Error in serialization
            e.printStackTrace();
        }
        PrivateKey myKey = Curve25519PrivateKey.random();
        PostOffice postOffice = attestation.createPostOffice(myKey, UUID.randomUUID().toString());
        byte[] encryptedMail = postOffice.encryptMail(stream);

        System.out.println("Sending the encrypted mail to the host.");
        toHost.writeInt(encryptedMail.length);
        toHost.write(encryptedMail);

        // Receive Enclave's reply
        byte[] encryptedReply = new byte[fromHost.readInt()];
        System.out.println("Reading reply mail of length " + encryptedReply.length + " bytes.");
        fromHost.readFully(encryptedReply);
        EnclaveMail reply = postOffice.decryptMail(encryptedReply);
        System.out.println("Enclave gave us the answer '" + new String(reply.getBodyAsBytes()) + "'");

        toHost.close();
        fromHost.close();

    }

    private static Pair<DataInputStream, DataOutputStream> establishConnection() throws Exception {
        DataInputStream fromHost;
        DataOutputStream toHost;
        while (true) {
            try {
                System.out.println("Attempting to connect to Host at localhost:5051");
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), 5051), 10000);
                fromHost = new DataInputStream(socket.getInputStream());
                toHost = new DataOutputStream(socket.getOutputStream());
                break;
            } catch (Exception e) {
                System.err.println("Retrying: " + e.getMessage());
                Thread.sleep(2000);
            }
        }
        return new Pair<>(fromHost, toHost);
    }

    private static EnclaveInstanceInfo verifyAttestation(DataInputStream fromHost, String constraint) throws Exception {
        byte[] attestationBytes = new byte[fromHost.readInt()];
        fromHost.readFully(attestationBytes);
        EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);
        System.out.println("Attestation Info received:  " + attestation);
        EnclaveConstraint.parse(constraint).check(attestation);
        return attestation;
    }
}