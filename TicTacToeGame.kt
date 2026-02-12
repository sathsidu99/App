package com.sasix.app

class TicTacToeGame {
    
    private val board = Array(9) { ' ' }
    var isPlayerTurn = true
    var isGameOver = false
    var winner: Char = ' '
    var isDraw = false
    
    private val winPatterns = arrayOf(
        intArrayOf(0, 1, 2),
        intArrayOf(3, 4, 5),
        intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6),
        intArrayOf(1, 4, 7),
        intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8),
        intArrayOf(2, 4, 6)
    )
    
    fun getBoardValue(index: Int): Char {
        return board[index]
    }
    
    fun isCellEmpty(index: Int): Boolean {
        return board[index] == ' '
    }
    
    fun makeMove(index: Int, player: Char): Boolean {
        if (isGameOver || board[index] != ' ') return false
        
        board[index] = player
        checkGameStatus()
        return true
    }
    
    fun computerMove() {
        if (isGameOver) return
        
        // 1. Try to win
        for (pattern in winPatterns) {
            val (a, b, c) = pattern
            if (board[a] == 'O' && board[b] == 'O' && board[c] == ' ') {
                makeMove(c, 'O')
                return
            }
            if (board[a] == 'O' && board[c] == 'O' && board[b] == ' ') {
                makeMove(b, 'O')
                return
            }
            if (board[b] == 'O' && board[c] == 'O' && board[a] == ' ') {
                makeMove(a, 'O')
                return
            }
        }
        
        // 2. Block player
        for (pattern in winPatterns) {
            val (a, b, c) = pattern
            if (board[a] == 'X' && board[b] == 'X' && board[c] == ' ') {
                makeMove(c, 'O')
                return
            }
            if (board[a] == 'X' && board[c] == 'X' && board[b] == ' ') {
                makeMove(b, 'O')
                return
            }
            if (board[b] == 'X' && board[c] == 'X' && board[a] == ' ') {
                makeMove(a, 'O')
                return
            }
        }
        
        // 3. Take center
        if (board[4] == ' ') {
            makeMove(4, 'O')
            return
        }
        
        // 4. Take corners
        val corners = arrayOf(0, 2, 6, 8)
        for (corner in corners) {
            if (board[corner] == ' ') {
                makeMove(corner, 'O')
                return
            }
        }
        
        // 5. Any empty cell
        for (i in 0..8) {
            if (board[i] == ' ') {
                makeMove(i, 'O')
                return
            }
        }
    }
    
    private fun checkGameStatus() {
        // Check winner
        for (pattern in winPatterns) {
            val (a, b, c) = pattern
            if (board[a] != ' ' && board[a] == board[b] && board[a] == board[c]) {
                winner = board[a]
                isGameOver = true
                return
            }
        }
        
        // Check draw
        if (board.all { it != ' ' }) {
            isDraw = true
            isGameOver = true
        }
    }
    
    fun newGame() {
        for (i in 0..8) {
            board[i] = ' '
        }
        isPlayerTurn = true
        isGameOver = false
        winner = ' '
        isDraw = false
    }
    
    fun resetGame() {
        newGame()
    }
}
