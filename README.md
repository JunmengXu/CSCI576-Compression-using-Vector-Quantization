# CSCI576-Compression-using-Vector-Quantization

CSCI 576 Assignment 2
Topic: Compression using Vector Quantization


INTRODUCTION
------------
Implement image compression using "vector quantization". 
The program works with one channel file.
The image size should be 352x288.
The original image is shown on the left side, the compressed image will be shown on the right side.
The first parameter is the filename of the input image, other two parameters are M and N, where the former is the size of the vectors, and the latter is the number of vectors in the code book.


COMMAND
-------
• MyCompression.exe myImage.rgb 2 N : 
        compress myImage.rgb, the size of the vectors is 2, number of vectors is N (power of 2)
	example: MyCompression.exe image1-onechannel.rgb 2 4

Extra

• MyCompression.exe myImage.rgb M N : 
        compress myImage.rgb, the size of the vectors is M (perfect square), number of vectors is N (power of 2)
	example: MyCompression.exe image3-onechannel.rgb 4 16


EXTRA CREDIT
------------
Extend the implementation for M = perfect square
Follow steps 1 through 5, but advance the algorithm to deal with higher dimensional representations.
Given a value for M, and N, the output picks out the best and most appropriate N square block, the size of the square will depend on M.


![Alt text](/assignment2.png?raw=true "Title")
