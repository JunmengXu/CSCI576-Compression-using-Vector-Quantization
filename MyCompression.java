
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MyCompression {

	JFrame frame;
	JLabel lbIm1;
	JLabel lbIm2;
	BufferedImage img;
	BufferedImage img_compressed;
	int width = 352;
	int height = 288;

	// used for judge cluster change in Extra function
	float lastRatioExtra = 1.0f;

	/*
	 * ======= Compression using Vector Quantization =======
	 */

	// Define a vector class for [pixel1, pixel2]
	// this is for M = 2
	public class myVector {
		int pixel1, pixel2;

		myVector(int pixel1, int pixel2) {
			this.pixel1 = pixel1;
			this.pixel2 = pixel2;
		}

		public int getPixel1(){
			return pixel1;
		}

		public int getPixel2(){
			return pixel2;
		}

		public double distance(myVector vector) {
			double sum = 0.0;
			double diff = pixel1 - vector.getPixel1();
			sum += diff * diff;
			diff = pixel2 - vector.getPixel2();
			sum += diff * diff;
			return Math.sqrt(sum);
		}

	}


	// Draws a black line on the given buffered image from the pixel defined by (x1, y1) to (x2, y2)
	public void drawLine(BufferedImage image, int x1, int y1, int x2, int y2) {
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1));
		g.drawLine(x1, y1, x2, y2);
		g.drawImage(image, 0, 0, null);
	}


	// Read the origianl file to produce the original image
	public void originalImage(String filename){
		// read image .rgb file
		File file = new File(filename);

		// each pixel takes up 1 byte because we are reading one channel images
		byte[] rgbData = new byte[width * height];

		// try to read the RGB data from the file into the byte array
		try {
			FileInputStream in = new FileInputStream(file);
			in.read(rgbData);
			in.close();
			System.out.println("Open file successfully: " + filename);

			// Initialize a plain white image
			img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

			int ind = 0;
			for(int y = 0; y < height; y++){
				for(int x = 0; x < width; x++){
					// byte a = (byte) 255;
					byte r = (byte) 255;
					byte g = (byte) 255;
					byte b = (byte) 255;

					int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
					//int pix = ((a << 24) + (r << 16) + (g << 8) + b);
					img.setRGB(x,y,pix);
					ind++;
				}
			}

			// set the RGB values for each pixel in the BufferedImage
			int index = 0;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int channel = rgbData[index] & 0xff;
					int rgb = (channel << 16) | (channel << 8) | channel;
					img.setRGB(x, y, rgb);
					index++;
				}
			}

		} catch (FileNotFoundException e) {
			System.out.println("Failed to find file: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("Failed to open file: " + e.getMessage());
		}
	}

	// Compress the image by vector quantization
	// here, we assume M is 2, and N (number of vectors) is a power of 2
	public void compressImage(int M, int N) {

		// Step 1: Understanding your two-pixel vector space to see what vectors your image contains
		// Create list of adjacent pixel vectors
		List<myVector> vectors = new ArrayList<>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x <= width - M; x+=2) {
				int pixel1 = getRGBValuesOneChannel( img.getRGB(x, y) );
				int pixel2 = getRGBValuesOneChannel( img.getRGB(x + 1, y) );
				myVector vector = new myVector(pixel1, pixel2);
				vectors.add(vector);
			}
		}

		// Step 2: Initialization of codewords - select N initial codewords
		Random random = new Random();
		List<myVector> codewords = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			int index = random.nextInt(vectors.size());
			codewords.add(vectors.get(index));
		}


		// Step 3: Clustering vectors around each code word
		// Step 4: Refine and Update your code words depending on outcome of 3
		boolean change = true; // whether codewords change or the change in the codewords is small.
		while(change){
			List<List<myVector>> clusters = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				clusters.add(new ArrayList<>());
			}
			for (myVector vector : vectors) {
				int nearestCodewordIndex = findNearestCodewordIndex(vector, codewords);
				clusters.get(nearestCodewordIndex).add(vector);
			}
			List<myVector> newCodewords = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				myVector averageVector = averageVectors(clusters.get(i));
				newCodewords.add(averageVector);
			}
			if (!codewordsChange(codewords, newCodewords, N)) {
				change = false;
				codewords = newCodewords;
			} else {
				codewords = newCodewords;
			}
		}

		// Step 5: Quantize input vectors to produce output image
		BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x <= width - M; x+=2) {
				int pixel1 = getRGBValuesOneChannel( img.getRGB(x, y) );
				int pixel2 = getRGBValuesOneChannel( img.getRGB(x + 1, y) );
				myVector vector = new myVector(pixel1, pixel2);
				int nearestCodewordIndex = findNearestCodewordIndex(vector, codewords);
				myVector quantizedVector = codewords.get(nearestCodewordIndex);
				int quantizedPixel1 = setRGBValuesOneChannel( quantizedVector.getPixel1() );
				int quantizedPixel2 = setRGBValuesOneChannel( quantizedVector.getPixel2() );
				outputImage.setRGB(x, y, quantizedPixel1);
				outputImage.setRGB(x + 1, y, quantizedPixel2);
			}
		}

		img_compressed = outputImage;
	}


	// Clusterize all the vectors, ie assign each vector to a codeword using the Euclidean distance measure.
	// The input vector belongs to the cluster of the codeword that yields the minimum distance.
	private int findNearestCodewordIndex(myVector vector, List<myVector> codewords) {
		int nearestIndex = 0;
		double nearestDistance = Double.MAX_VALUE;
		for (int i = 0; i < codewords.size(); i++) {
			double distance = vector.distance(codewords.get(i));
			if (distance < nearestDistance) {
				nearestIndex = i;
				nearestDistance = distance;
			}
		}
		return nearestIndex;
	}


	// Transfer a three channels rgb value into one channel value
	private int getRGBValuesOneChannel(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		return r;
	}


	// Transfer a one channel value into three channels rgb value
	private int setRGBValuesOneChannel(int r) {
		int channel = r & 0xff;
		int rgb = (channel << 16) | (channel << 8) | channel;
		return rgb;
	}


	// Updated position of each codeword by the average of each cluster
	private myVector averageVectors(List<myVector> list){
		int pixel1sum = 0;
		int pixel2sum = 0;
		for (int i = 0; i < list.size(); i++) {
			pixel1sum += list.get(i).getPixel1();
			pixel2sum += list.get(i).getPixel2();
		}
		int pixel1 = Math.round((float) pixel1sum / list.size());
		int pixel2 = Math.round((float) pixel2sum / list.size());
		myVector myvector = new myVector(pixel1, pixel2);
		return myvector;
	}


	// Judge whether the codewords don’t change or the change in the codewords is small
	private boolean codewordsChange(List<myVector> codewords, List<myVector> newCodewords, int N){
		int times = 0;
		for(int i=0; i<codewords.size(); i++){
			myVector vectorOld = codewords.get(i);
			myVector vectorNew = newCodewords.get(i);
			if(vectorOld.getPixel1() != vectorNew.getPixel1() || vectorOld.getPixel2() != vectorNew.getPixel2()){
				times++;
			}
		}
		float timesf = (float) times;
		float Nf = (float) N;
		float ratio = timesf / Nf;
		if(ratio < 0.01){
			return false;
		}else{
			return true;
		}
	}


	// To judge whether a given number N is a power of 2 or not
	private boolean isPowerOf2(int N) {
		return (N & (N - 1)) == 0 && N > 0;
	}


	// To check whether a given number X is a perfect square
	private boolean isPerfectSquare(int X) {
		int root = (int) Math.sqrt(X);
		return root * root == X;
	}


	// Show two images onn the screen
	public void showIms(BufferedImage img1, BufferedImage img2){
		// draw border for image 1
		drawLine(img1, 0, 0, width-1, 0);				// top edge
		drawLine(img1, 0, 0, 0, height-1);				// left edge
		drawLine(img1, 0, height-1, width-1, height-1);	// bottom edge
		drawLine(img1, width-1, height-1, width-1, 0); 	// right edge

		// draw border for image 2
		drawLine(img2, 0, 0, width-1, 0);				// top edge
		drawLine(img2, 0, 0, 0, height-1);				// left edge
		drawLine(img2, 0, height-1, width-1, height-1);	// bottom edge
		drawLine(img2, width-1, height-1, width-1, 0); 	// right edge

		// Use labels to display the images
		frame = new JFrame();
		GridBagLayout gLayout = new GridBagLayout();
		frame.getContentPane().setLayout(gLayout);

		JLabel lbText1 = new JLabel("Original image (Left)");
		lbText1.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel lbText2 = new JLabel("Image after compressed (Right)");
		lbText2.setHorizontalAlignment(SwingConstants.CENTER);
		lbIm1 = new JLabel(new ImageIcon(img1));
		lbIm2 = new JLabel(new ImageIcon(img2));

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;
		c.gridx = 0;
		c.gridy = 0;
		frame.getContentPane().add(lbText1, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = 0;
		frame.getContentPane().add(lbText2, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 1;
		frame.getContentPane().add(lbIm1, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 1;
		c.gridy = 1;
		frame.getContentPane().add(lbIm2, c);

		frame.pack();
		frame.setVisible(true);
	}

	// Contains the whole process, read original image, compress it to make a compressed one
	// and show them both on the screen
	public void processImage(String filename, int M, int N){
		originalImage(filename);

		if(!isPowerOf2(N)){
			System.out.println("N is not a power of N, please enter again.");
			return;
		}
		if(M != 2 && !isPerfectSquare(M)){
			System.out.println("M is not 2 or perfect square, please enter again.");
			return;
		}

		if(M == 2){
			System.out.println("M = 2");
			System.out.println("Waiting for compressing...");
			compressImage(M, N);
		}else{
			System.out.println("Extra: M = perfect square");
			System.out.println("Waiting for compressing...");
			compressImageExtra(M, N);
		}
		System.out.println("Compressed done!");

		showIms(img, img_compressed);
	}



	/*
	 * ======= Extra Credit =======
	 */

	// Define a vector class for M pixels
	// this is for M = perfect square
	public class myVectorExtra {
		List<Integer> pixels;

		myVectorExtra(List<Integer> pixels) {
			this.pixels = new ArrayList<Integer>(pixels);
		}

		public List<Integer> getPixels(){
			return pixels;
		}

		public double distance(myVectorExtra vector) {
			double sum = 0.0;
			int M = pixels.size();
			List<Integer> pixels2 = vector.getPixels();
			for(int i=0; i<M; i++){
				double diff = pixels.get(i) - pixels2.get(i);
				sum += diff * diff;
			}
			return Math.pow(sum, 1.0 / M);
		}

	}

	// Compress the image by vector quantization
	// here, we assume M is perfect square, eg – M = 4 (2x2 blocks), 9 (3x3 blocks) etc.
	// and N (number of vectors) is a power of 2
	public void compressImageExtra(int M, int N){

		// Step 1: Understanding your two-pixel vector space to see what vectors your image contains
		// Create list of adjacent blocks pixel vectors
		int root = (int) Math.sqrt(M);
		List<myVectorExtra> vectors = new ArrayList<>();
		int y = 0;
		int x = 0;
		while(y <= height - root){
			x = 0;
			while(x <= width - root){
				List<Integer> pixels = new ArrayList<Integer>();
				for(int i=0; i<root; i++){
					for(int j=0; j<root; j++){
						pixels.add( getRGBValuesOneChannel( img.getRGB(x+j, y+i) ) );
					}
				}
				myVectorExtra vector = new myVectorExtra(pixels);
				vectors.add(vector);
				if(x + root <= width - root){
					x += root;
				}else if(x + root > width - root && x + root < width){
					x = width - root;
				}else{
					x = width;
				}
			}
			if(y + root <= height - root){
				y += root;
			}else if(y + root > height - root && y + root < height){
				y = height - root;
			}else{
				y = height;
			}
		}


		// Step 2: Initialization of codewords - select N initial codewords
		Random random = new Random();
		List<myVectorExtra> codewords = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			int index = random.nextInt(vectors.size());
			codewords.add(vectors.get(index));
		}


		// Step 3: Clustering vectors around each code word
		// Step 4: Refine and Update your code words depending on outcome of 3
		boolean change = true; // whether codewords change or the change in the codewords is small.
		// Here, because of higher dimensional representations (higher M)
		// the sensitivity to change will be reduced, because too many traversals will make the program run longer
		while(change){
			List<List<myVectorExtra>> clusters = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				clusters.add(new ArrayList<>());
			}
			for (myVectorExtra vector : vectors) {
				int nearestCodewordIndex = findNearestCodewordIndexExtra(vector, codewords);
				clusters.get(nearestCodewordIndex).add(vector);
			}
			List<myVectorExtra> newCodewords = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				myVectorExtra averageVector = averageVectorsExtra(clusters.get(i), M);
				newCodewords.add(averageVector);
			}
			if (!codewordsChangeExtra(codewords, newCodewords, N)) {
				change = false;
				codewords = newCodewords;
			} else {
				codewords = newCodewords;
			}
		}

		// Step 5: Quantize input vectors to produce output image
		BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		y = 0;
		while(y <= height - root){
			x = 0;
			while(x <= width - root){
				List<Integer> pixels = new ArrayList<Integer>();
				for(int i=0; i<root; i++){
					for(int j=0; j<root; j++){
						pixels.add( getRGBValuesOneChannel( img.getRGB(x+j, y+i) ) );
					}
				}

				myVectorExtra vector = new myVectorExtra(pixels);
				int nearestCodewordIndex = findNearestCodewordIndexExtra(vector, codewords);
				myVectorExtra quantizedVector = codewords.get(nearestCodewordIndex);
				List<Integer> quantizedPixels = quantizedVector.getPixels();

				int index = 0;
				for(int i=0; i<root; i++){
					for(int j=0; j<root; j++){
						int quantizedPixel = setRGBValuesOneChannel( quantizedPixels.get(index) );
						index++;
						outputImage.setRGB(x+j, y+i, quantizedPixel);
					}
				}
				if(x + root <= width - root){
					x += root;
				}else if(x + root > width - root && x + root < width){
					x = width - root;
				}else{
					x = width;
				}
			}
			if(y + root <= height - root){
				y += root;
			}else if(y + root > height - root && y + root < height){
				y = height - root;
			}else{
				y = height;
			}
		}

		img_compressed = outputImage;
	}


	// Clusterize all the vectors, ie assign each vector to a codeword using the Euclidean distance measure.
	// The input vector belongs to the cluster of the codeword that yields the minimum distance.
	private int findNearestCodewordIndexExtra(myVectorExtra vector, List<myVectorExtra> codewords) {
		int nearestIndex = 0;
		double nearestDistance = Double.MAX_VALUE;
		for (int i = 0; i < codewords.size(); i++) {
			double distance = vector.distance(codewords.get(i));
			if (distance < nearestDistance) {
				nearestIndex = i;
				nearestDistance = distance;
			}
		}
		return nearestIndex;
	}


	// Updated position of each codeword by the average of each cluster
	private myVectorExtra averageVectorsExtra(List<myVectorExtra> list, int M){
		int[] pixelSums = new int[M];
		for (int i = 0; i < list.size(); i++) {
			myVectorExtra vector = list.get(i);
			List<Integer> pixels = vector.getPixels();
			for(int j=0; j<pixels.size(); j++){
				pixelSums[j] += pixels.get(j);
			}
		}
		List<Integer> newPixels = new ArrayList<Integer>();
		for(int i=0; i<M; i++){
			newPixels.add( Math.round((float) pixelSums[i] / list.size()) );
		}
		myVectorExtra myvector = new myVectorExtra(newPixels);
		return myvector;
	}


	// Judge whether the codewords don’t change or the change in the codewords is small
	private boolean codewordsChangeExtra(List<myVectorExtra> codewords, List<myVectorExtra> newCodewords, int N){
		int times = 0;
		for(int i=0; i<codewords.size(); i++){
			myVectorExtra vectorOld = codewords.get(i);
			myVectorExtra vectorNew = newCodewords.get(i);
			List<Integer> pixelsOld = vectorOld.getPixels();
			List<Integer> pixelsNew = vectorNew.getPixels();
			boolean change = false;
			for(int j=0; j<pixelsOld.size(); j++){
				if(pixelsOld.get(j) != pixelsNew.get(j)){
					change = true;
					break;
				}
			}
			if(change){
				times++;
			}
		}
		float timesf = (float) times;
		float Nf = (float) N;
		float ratio = timesf / Nf;
		if(ratio < 0.1 || lastRatioExtra-ratio<0.05){
			lastRatioExtra = ratio;
			return false;
		}else{
			return true;
		}
	}



	/*
	 * ======= Main function =======
	 */

	public static void main(String[] args) {
		// Read first parameter from command line
		String filename = args[0];
		System.out.println("The parameter filename was: " + filename);

		// Read the second parameter from command line
		int M = Integer.valueOf(args[1]);
		System.out.println("The parameter M was: " + M);

		// Read the third parameter from command line
		int N = Integer.valueOf(args[2]);
		System.out.println("The parameter N was: " + N);

		MyCompression ren = new MyCompression();
		ren.processImage(filename, M, N);
	}
}