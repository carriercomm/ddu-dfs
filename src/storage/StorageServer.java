package storage;

import static java.lang.System.out;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import naming.Registration;
import rmi.RMIException;
import rmi.Skeleton;
import rmi.Stub;

import common.Path;

/**
 * Storage server.
 * 
 * <p>
 * Storage servers respond to client file access requests. The files accessible through a storage server are those accessible
 * under a given directory of the local filesystem.
 */
public class StorageServer implements Storage, Command {
    File root;
    Skeleton<Storage> clientSkeleton;
    Skeleton<Command> commandSkeleton;
    int clientPort;
    int commandPort;
    static int DEFAULT_CLIENT_PORT = 7225;
    static int DEFAULT_COMMAND_PORT = 9325;

    /**
     * Creates a storage server, given a directory on the local filesystem, and ports to use for the client and command
     * interfaces.
     * 
     * <p>
     * The ports may have to be specified if the storage server is running behind a firewall, and specific ports are open.
     * 
     * @param root
     *            Directory on the local filesystem. The contents of this directory will be accessible through the storage server.
     * @param client_port
     *            Port to use for the client interface, or zero if the system should decide the port.
     * @param command_port
     *            Port to use for the command interface, or zero if the system should decide the port.
     * @throws NullPointerException
     *             If <code>root</code> is <code>null</code>.
     */
    public StorageServer(File root, int client_port, int command_port) {
        if (root == null) {
            throw new NullPointerException("Root is null");
        }

        if (!root.exists()) {
            root.mkdirs();
        }

        this.root = root;
        InetSocketAddress clientAddr;
        InetSocketAddress commandAddr;
        // initializes the client port only if it is a valid port
        if (client_port > 0) {
            clientAddr = new InetSocketAddress(client_port);
            clientSkeleton = new Skeleton<Storage>(Storage.class, this, clientAddr);
        } else {
            clientSkeleton = new Skeleton<Storage>(Storage.class, this);
        }
        // initializes the command port only if it is a valid port
        if (command_port > 0) {
            commandAddr = new InetSocketAddress(command_port);
            commandSkeleton = new Skeleton<Command>(Command.class, this, commandAddr);
        } else {
            commandSkeleton = new Skeleton<Command>(Command.class, this);
        }
    }

    /**
     * Creats a storage server, given a directory on the local filesystem.
     * 
     * <p>
     * This constructor is equivalent to <code>StorageServer(root, 0, 0)</code>. The system picks the ports on which the
     * interfaces are made available.
     * 
     * @param root
     *            Directory on the local filesystem. The contents of this directory will be accessible through the storage server.
     * @throws NullPointerException
     *             If <code>root</code> is <code>null</code>.
     */
    public StorageServer(File root) {
        this(root, 0, 0);
    }

    /**
     * Starts the storage server and registers it with the given naming server.
     * 
     * @param hostname
     *            The externally-routable hostname of the local host on which the storage server is running. This is used to
     *            ensure that the stub which is provided to the naming server by the <code>start</code> method carries the
     *            externally visible hostname or address of this storage server.
     * @param naming_server
     *            Remote interface for the naming server with which the storage server is to register.
     * @throws UnknownHostException
     *             If a stub cannot be created for the storage server because a valid address has not been assigned.
     * @throws FileNotFoundException
     *             If the directory with which the server was created does not exist or is in fact a file.
     * @throws RMIException
     *             If the storage server cannot be started, or if it cannot be registered.
     */
    public synchronized void start(String hostname, Registration naming_server) throws RMIException, UnknownHostException,
            FileNotFoundException {
        if (!root.exists() || root.isFile()) {
            throw new FileNotFoundException("Directory with which the server was" + "created does not exist or is in fact a file");
        }
        clientSkeleton.start();
        commandSkeleton.start();
        Storage clientStub = (Storage) Stub.create(Storage.class, clientSkeleton, hostname);
        Command commandStub = (Command) Stub.create(Command.class, commandSkeleton, hostname);
        Path[] files = Path.list(root);
        naming_server.register(clientStub, commandStub, files);
        // // delete all duplicate files
        // for (Path p : duplicateFiles) {
        // p.toFile(root).delete();
        // // prune all empty directories
        // deleteEmpty(new File(p.toFile(root).getParent()));
        // }
    }

    /*
     * Deletes directories if they are empty
     */
    public synchronized void deleteEmpty(File parent) {
        // cannot delete the root
        while (!parent.equals(root)) {
            // if the parent directory does not have children, deletes parent
            if (parent.list().length == 0) {
                parent.delete();
            } else {
                break;
            }
            parent = new File(parent.getParent());
        }
    }

    /**
     * Stops the storage server.
     * 
     * <p>
     * The server should not be restarted.
     */
    public void stop() {
        clientSkeleton.stop();
        commandSkeleton.stop();
        stopped(null);
    }

    /**
     * Called when the storage server has shut down.
     * 
     * @param cause
     *            The cause for the shutdown, if any, or <code>null</code> if the server was shut down by the user's request.
     */
    protected void stopped(Throwable cause) {
    }

    // The following methods are documented in Storage.java.
    @Override
    public synchronized long size(Path file) throws FileNotFoundException {
        File f = file.toFile(root);
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("File cannot be found or refers to" + "a directory");
        }
        return f.length();
    }

    @Override
    public synchronized byte[] read(Path file, long offset, int length) throws FileNotFoundException, IOException {
        File f = file.toFile(root);
        out.println("the fire to read is " + f.toString());
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("File cannot be found or refers to" + "a directory");
        }
        if ((offset < 0) || (length < 0) || (offset + length > f.length())) {
            throw new IndexOutOfBoundsException("Sequence specified is outside"
                    + "of the bounds of the file, or length is negative");
        }
        // reads from the file using FileInputStream and returns the content
        InputStream reader = new FileInputStream(f);
        byte[] output = new byte[length];
        reader.read(output, (int) offset, length);
        reader.close();
        return output;
    }

    @SuppressWarnings("resource")
    @Override
    public byte[] read(Path file) throws RMIException, FileNotFoundException, IOException {
        File file2 = file.toFile(root);
        out.println("file to be read is " + file.toString());
        if (!file2.exists()) {
            out.println("error file does not exists!");
        }
        InputStream reader = new FileInputStream(file2);

        byte[] buffer = new byte[(int) file2.length()];

        reader.read(buffer);

        reader.close();
        return buffer;
    }

    @Override
    public synchronized void append(Path file, byte[] data) throws RMIException, FileNotFoundException, IOException {
        this.write(file, data, true);
    }

    @Override
    public void write(Path file, byte[] data) throws RMIException, FileNotFoundException, IOException {
        this.write(file, data, false);
    }

    private void write(Path file, byte[] data, boolean append) throws RMIException, FileNotFoundException, IOException {
        File f = file.toFile(root);
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("File cannot be found or refers to" + "a directory");
        }

        FileOutputStream writer = new FileOutputStream(f, append);
        writer.write(data);
        writer.close();
    }

    @Override
    public void write(Path file, long offset, byte[] data) throws FileNotFoundException, IOException {
        File f = file.toFile(root);
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("File cannot be found or refers to" + "a directory");
        }
        if (offset < 0) {
            throw new IndexOutOfBoundsException("The offset is negative");
        }
        InputStream reader = new FileInputStream(f);
        FileOutputStream writer = new FileOutputStream(f);

        // determinds how many bytes are to be read
        long readLength = Math.min(offset, f.length());
        byte[] offsetBytes = new byte[(int) readLength];
        // reads from the data and writes to the file
        reader.read(offsetBytes);
        writer.write(offsetBytes, 0, (int) readLength);
        long fillLength = offset - f.length();
        if (fillLength > 0) {
            for (int i = 0; i < (int) fillLength; i++) {
                writer.write(0);
            }
        }
        reader.close();
        writer.write(data);
        writer.close();
    }

    // The following methods are documented in Command.java.
    @Override
    public boolean create(Path file) {
        if (file == null) {
            throw new NullPointerException("Given a null argument");
        }
        if (file.isRoot()) {
            out.println("file is root!");
            return false;
        }

        out.println("create file path is " + file.toString());

        Path parent = file.parent();

        // check parent dir exists
        File parentFile = parent.toFile(root);

        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }

        // creates the file
        File f = file.toFile(root);
        try {
            return f.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public synchronized boolean delete(Path path) {
        // cannot delete the root
        if (path.isRoot()) {
            return false;
        }
        // deletes the file
        File f = path.toFile(root);
        if (f.isFile()) {
            return f.delete();
        } else {
            return deleteHelper(f);
        }
    }

    // a helper method for the delete method
    private boolean deleteHelper(File f) {
        if (f.isDirectory()) {
            File[] subfiles = f.listFiles();
            for (File subf : subfiles) {
                if (!deleteHelper(subf)) {
                    return false;
                }
            }
        }
        return f.delete();
    }

    @Override
    public boolean copy(Path file, Storage server) throws RMIException, FileNotFoundException, IOException {

        out.println("coming to copy!");

        // deletes the given file if it already exists on the server
        File f = file.toFile(root);
        if (f.isDirectory()) {
            return true;
        }
        if (f.exists()) {
            delete(file);
        }
        // creates the file on this server and copies bytes over from the other
        create(file);

        byte[] data = server.read(file);

        this.write(file, data);

        out.println("copy file done!");
        //
        // long fileSize = server.size(file);
        // long offset = 0;
        // while (offset < fileSize) {
        // int bytesToCopy = (int) Math.min(Integer.MAX_VALUE, fileSize - offset);
        // byte[] data = server.read(file, offset, bytesToCopy);
        // write(file, offset, data);
        // offset += bytesToCopy;
        // }
        return true;
    }

    @Override
    public synchronized byte[] randomRead(Path file, long offset, int length) throws RMIException, FileNotFoundException,
            IOException {
        File f = file.toFile(root);
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("File cannot be found or refers to" + "a directory");
        }
        if ((offset < 0) || (length < 0) || (offset + length > f.length())) {
            throw new IndexOutOfBoundsException("Sequence specified is outside"
                    + "of the bounds of the file, or length is negative");
        }
        // reads from the file using FileInputStream and returns the content
        RandomAccessFile raf = new RandomAccessFile(f, "r");

        System.out.println("RandomAccessFile pointer start at " + raf.getFilePointer());
        raf.seek(offset);
        System.out.println("RandomAccessFile pointer now at " + raf.getFilePointer());

        byte[] output = new byte[length];
        raf.read(output);
        return output;
    }

    @SuppressWarnings("resource")
    @Override
    public synchronized void randomWrite(Path file, long offset, byte[] data) throws RMIException, FileNotFoundException,
            IOException {
        File f = file.toFile(root);
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("File cannot be found or refers to" + "a directory");
        }
        if (offset > f.length()) {
            throw new IndexOutOfBoundsException("Sequence specified is outside"
                    + "of the bounds of the file, or length is negative");
        }

        System.out.print("randomWrite input file is " + file.toString());
        File tmp = createTmpFileInCurrentFileDiretory(file);
        System.out.print("randomWrite tmp file is " + tmp.toString());
        tmp.deleteOnExit();// 在JVM退出时删除

        // 创建一个临时文件来保存插入点后的数据
        FileOutputStream tmpOut = new FileOutputStream(tmp);
        FileInputStream tmpIn = new FileInputStream(tmp);

        // reads from the file using FileInputStream and returns the content
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        System.out.println("offset is " + offset);
        raf.seek(offset);

        byte[] buff = new byte[1024];
        // 用于保存临时读取的字节数
        int hasRead = 0;
        // 循环读取插入点后的内容
        while ((hasRead = raf.read(buff)) > 0) {
            // 将读取的数据写入临时文件中
            System.out.println("write tmp hasRead value is" + hasRead);
            System.out.println("buff is " + new String(buff, 0, hasRead));
            tmpOut.write(buff, 0, hasRead);
        }
        // 返回原来的插入处
        raf.seek(offset);
        raf.write(data);

        // 最后追加临时文件中的内容
        while ((hasRead = tmpIn.read(buff)) > 0) {
            System.out.println("read tmp hasRead value is" + hasRead);
            raf.write(buff, 0, hasRead);
        }

        if (tmpIn != null) {
            try {
                tmpIn.close();
            } catch (Throwable t) {
            }
        }

        if (tmpOut != null) {
            try {
                tmpOut.close();
            } catch (Throwable t) {
            }
        }

        deleteHelper(tmp);

    }

    private File createTmpFileInCurrentFileDiretory(Path file) {

        Path parent = file.parent();
        Path tmp = new Path(parent, "tmasfefas123d66fp.txt");
        return tmp.toFile(root);

    }

    @Override
    public boolean isFileExist(Path path) throws RMIException {
        File file = path.toFile(root);
        return file.exists();
    }

}
